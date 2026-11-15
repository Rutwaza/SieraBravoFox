import java.io.File
import com.android.build.api.variant.ApplicationAndroidComponentsExtension
import com.android.build.api.dsl.ApplicationExtension

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.nexrtz.andload"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.nexrtz.andload"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.10"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    
    // Media
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.ui)
    implementation(libs.androidx.media3.session)
    implementation(libs.coil.compose)

    implementation(libs.androidx.appcompat)
    implementation(libs.material)
}

// Background DEX loader tasks
val compilePlugin = tasks.register<JavaCompile>("compilePlugin") {
    group = "custom"
    source = fileTree("src/main/java/com/nexrtz/plugin") { include("ComputePlugin.java") }
    classpath = files()
    destinationDirectory.set(layout.buildDirectory.dir("plugin_classes"))
    sourceCompatibility = "1.8"
    targetCompatibility = "1.8"
}

tasks.register("buildPluginDex") {
    group = "custom"
    dependsOn(compilePlugin)
    doLast {
        val buildDir = layout.buildDirectory.get().asFile
        val outputDir = File(buildDir, "plugin_output")
        outputDir.mkdirs()
        val classFile = File(buildDir, "plugin_classes/com/nexrtz/plugin/ComputePlugin.class")
        
        val androidComponents = project.extensions.getByType(ApplicationAndroidComponentsExtension::class.java)
        val sdkDir = androidComponents.sdkComponents.sdkDirectory.get().asFile
        val androidExtension = project.extensions.getByType(ApplicationExtension::class.java)
        val buildToolsVersion = androidExtension.buildToolsVersion ?: "34.0.0"
        
        val isWindows = System.getProperty("os.name").lowercase().contains("win")
        val d8Path = File(sdkDir, "build-tools/$buildToolsVersion/${if (isWindows) "d8.bat" else "d8"}").absolutePath
        
        ProcessBuilder(d8Path, classFile.absolutePath, "--output", outputDir.absolutePath).inheritIO().start().waitFor()
        
        val dexFile = File(outputDir, "classes.dex")
        if (dexFile.exists()) {
            dexFile.copyTo(project.file("classes.dex"), overwrite = true)
        }
    }
}
