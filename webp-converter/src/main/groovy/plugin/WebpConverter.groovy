package plugin

import com.android.build.gradle.AppPlugin
import org.gradle.api.Plugin
import org.gradle.api.Project

import javax.imageio.ImageIO

/**
 * jpg/png->webp plugin
 */
public class WebpConverter implements Plugin<Project> {

    WebpConfig config

    @Override
    void apply(Project project) {
        config = project.extensions.create("webpConfig", WebpConfig);
        def hasApp = project.plugins.withType(AppPlugin)
        def variants = hasApp ? project.android.applicationVariants : project.android.libraryVariants
        project.afterEvaluate {
            variants.all { variant ->
                def buildType = variant.getVariantData().getVariantConfiguration().getBuildType().name
                if(!config.enabled) return
                if (config.skipDebug && "${buildType}".contains("debug")) {
                    printLog "skipDebug webpConvert Task!!!!!!"
                    return
                }

                def apiLevel = project.android.defaultConfig.minSdkVersion.mApiLevel
                def dx = project.tasks.findByName("process${variant.name.capitalize()}Resources")
                def webpConvertPlugin = "webpConvert${variant.name.capitalize()}"
                project.task(webpConvertPlugin) << {
                    String resPath = variant.mergeResources.outputDir
                    def dir = new File("${resPath}")
                    printLog "resPath:" + resPath
                    int count = 0
                    dir.eachDirMatch(~/^drawable.*|^mipmap.*/) { drawDir ->
                        printLog "drawableDir:" + drawDir
                        def file = new File("${drawDir}")
                        file.eachFile { imageFile ->
                            def name = imageFile.name
                            def f = new File("${project.projectDir}/webp_white_list.txt")
                            if (!f.exists()) {
                                f.createNewFile()
                            }
                            def isInWhiteList = false
                            f.eachLine { whiteName ->
                                if (name.equals(whiteName)) {
                                    isInWhiteList = true
                                }
                            }
                            if (!isInWhiteList && shouldConvert(apiLevel, imageFile)) {
                                def picName = name[0..webpFileName.lastIndexOf('.')-1]
                                printLog "find target pic >>>>>>>>>>>>>" + name
                                def webpFile = new File("${drawDir}/${picName}.webp")
                                def cwebp = [
                                        "${config.cwebp}",
                                        "-q",
                                        "${config.quality}",
                                        "-m",
                                        "6",
                                        imageFile,
                                        "-o",
                                        webpFile
                                ]
                                .execute()
                                cwebp.waitFor()
                                if(cwebp.exitValue() != 0) {
                                    logger.error("cwebp with error code ${cwebp.exitValue()} and: ${cwebp.err.text}")
                                } else {
                                    if(webpFile.length() < imageFile.length()) {
                                        count++;
                                        imageFile.delete()
                                        printLog "generate:" + imageFile.name
                                    } else {
                                        webpFile.delete()
                                    }
                                }
                            }

                        }
                    }
                    println "webpConvert: files convert " + count
                }

                project.tasks.findByName(webpConvertPlugin).dependsOn dx.taskDependencies.getDependencies(dx)
                dx.dependsOn project.tasks.findByName(webpConvertPlugin)
            }

        }
    }

    boolean shouldConvert(def apiLevel, File file) {
        if (file.name.matches(/.+\.9\..*/))
            return false
        printLog "check file:" + file.name
        //Transparency/Alpha channel requires api 18
        if (!config.apiCompat || apiLevel >= 18) {
            return file.name.matches(/.+\.(jpg|png)$/)
        } else {
            return file.name.matches(/.+\.(jpg|png)$/) && !ImageIO.read(file).colorModel.hasAlpha()
        }

    }

    void printLog(String msg) {
        if (config.showLog) {
            println msg
        }
    }
}

