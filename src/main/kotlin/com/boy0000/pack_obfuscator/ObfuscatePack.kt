package com.boy0000.pack_obfuscator

import com.boy0000.pack_obfuscator.ObfuscatePack.packPath
import com.boy0000.pack_obfuscator.ObfuscatePack.texturePath
import com.google.gson.JsonParser
import com.mineinabyss.idofront.messaging.broadcast
import com.mineinabyss.idofront.messaging.broadcastVal
import com.mineinabyss.idofront.messaging.logSuccess
import io.th0rgal.oraxen.OraxenPlugin
import org.bukkit.Material
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.*
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

data class ObfuscatedModel(val modelPath: String, val obfuscatedModelName: String)
data class ObfuscatedTexture(val texturePath: String, val obfuscatedTextureName: String)

object ObfuscatePack {

    private val tempPackDir: File = Files.createTempDirectory("tempPack").toFile().apply { deleteOnExit() }
    val obfuscatedMap = mutableMapOf<ObfuscatedModel, MutableSet<ObfuscatedTexture>>()
    fun obfuscate(pack: File) {
        broadcast(tempPackDir.absolutePath)
        unzip(pack.absolutePath, tempPackDir.absolutePath)

        val packFiles = tempPackDir.listFilesRecursively()
        val models = packFiles.filter { it.isModel && !it.isVanillaBaseModel }
        val textures = packFiles.filter { it.isTexture }
        broadcast("Obfuscating ${textures.size} textures and ${models.size} models")

        obfuscateModels(models, textures)
        obfuscateParentModels(packFiles)
        obfuscateAtlas()

        copyAndCleanup()
    }

    private fun copyAndCleanup() {
        val packDirNew = File("C:\\Users\\Sivert\\AppData\\Roaming\\.mineinabyss\\resourcepacks")
        packDirNew.listFiles()?.filter { it.nameWithoutExtension.startsWith("tempPack") }?.forEach(File::deleteRecursively)
        tempPackDir.copyRecursively(packDirNew.resolve(tempPackDir.name), true)
        tempPackDir.parentFile.listFiles()?.filter { it.nameWithoutExtension.startsWith("tempPack") }?.forEach(File::deleteRecursively)
    }

    private fun obfuscateAtlas() {
        val atlas = File(tempPackDir, "assets/minecraft/atlases/blocks.json")
        val atlasJson = JsonParser.parseString(atlas.readText()).asJsonObject
        val sources = atlasJson.getAsJsonArray("sources") ?: return
        val obfuscatedTextures = obfuscatedMap.values.flatten().map { ObfuscatedTexture(it.texturePath.substringBetween("textures/", ".png"), it.obfuscatedTextureName) }
        sources.map { it.asJsonObject }.forEach {
            if (it.get("type").asString != "single") return@forEach
            val resource = it.get("resource").asString.replace("minecraft:", "")
            val texture = obfuscatedTextures.find { it.texturePath == resource } ?: return@forEach
            if (resource == texture.texturePath) it.addProperty("resource", "minecraft:"+texture.obfuscatedTextureName)
            if (it.get("sprite").asString == "minecraft:"+texture.texturePath) it.addProperty("sprite", "minecraft:"+texture.obfuscatedTextureName)
            broadcast("Obfuscated $resource to ${texture.obfuscatedTextureName}")
            sources.add(it)
        }
        atlasJson.add("sources", sources)
        atlas.writeText(atlasJson.toString())
    }

    private fun obfuscateParentModels(packFiles: List<File>) {
        val obfuscatedModelKeys = obfuscatedMap.keys.map {
            val namespace = it.modelPath .substringBetween("assets/", "/models")
            val path = it.modelPath.substringBetween("/models/", ".json")
            ObfuscatedModel(if (namespace == "minecraft") path else "$namespace:$path", it.obfuscatedModelName)
        }
        packFiles.filter { it.isModel && it.isVanillaBaseModel }.forEach baseModel@{ vanillaBaseModel ->
            val baseModelJson = JsonParser.parseString(vanillaBaseModel.readText()).asJsonObject
            val overrides = baseModelJson.getAsJsonArray("overrides") ?: return@baseModel
            overrides.forEach overrides@{ override ->
                val overrideModel = override.asJsonObject.getAsJsonPrimitive("model").asString
                val obfuscatedModel = obfuscatedModelKeys.find { it.modelPath == overrideModel.replace("\"", "") }?.obfuscatedModelName ?: return@overrides
                override.asJsonObject.addProperty("model", obfuscatedModel)
            }
            baseModelJson.add("overrides", overrides)
            vanillaBaseModel.writeText(baseModelJson.toString())
        }
    }

    private fun obfuscateModels(models: List<File>, textureFiles: List<File>) {
        models.forEach models@{ model ->
            if (!model.exists()) return@models
            val modelJson = JsonParser.parseString(model.readText()).asJsonObject
            val modelTextures = modelJson.getAsJsonObject("textures")?.asMap()?.values?.map { it.toString().replace("\"", "") } ?: return@models
            val obfuscatedModelName = UUID.randomUUID().toString()

            modelTextures.forEach textures@{ texture ->
                val obfuscatedTextureName = UUID.randomUUID().toString()
                val textureFile = textureFiles.find { texture in it.texturePath } ?: return@textures
                textureFile.renameTo(File(textureFile.parentFile, "$obfuscatedTextureName.png"))
                modelJson.getAsJsonObject("textures").let { it.addProperty(it.entrySet().find { it.value.toString() == "\"$texture\"" }!!.key, obfuscatedTextureName) }
                obfuscatedMap.computeIfAbsent(ObfuscatedModel(model.packPath, obfuscatedModelName)) { mutableSetOf() } += ObfuscatedTexture(textureFile.packPath, obfuscatedTextureName)
            }

            model.writeText(modelJson.toString())
            model.renameTo(File(model.parentFile, "$obfuscatedModelName.json"))
        }
    }

    private val File.packPath get() = this.path.removePrefix(tempPackDir.absolutePath).drop(1).replace("\\", "/").replace(".png", "")
    private val File.texturePath get(): String {
        val namespace = this.packPath.substringAfter("assets/").substringBefore("/")
        val path = this.packPath.substringAfter("$namespace/textures/")
        return if (namespace == "minecraft") path else "$namespace:$path"
    }
    private val File.isModel get() = this.extension == "json"
    private val File.isTexture get() = this.extension == "png" || this.extension == "mcmeta"
    private val File.isVanillaBaseModel get(): Boolean {
        val isBase = this.isModel && "assets\\minecraft\\models\\item" in this.path || "assets\\minecraft\\models\\block" in this.path
        return isBase && Material.matchMaterial(this.nameWithoutExtension) != null
    }

    private fun String.substringBetween(start: String, end: String) = this.substringAfter(start).substringBefore(end)

    private fun File.listFilesRecursively() = mutableListOf<File>().apply {
        walkTopDown().forEach { this += it }
    }

    fun unzip(zipFilePath: String, destinationFolderPath: String) {
        val zipFile = ZipFile(zipFilePath)
        val destinationFolder = File(destinationFolderPath)

        if (!destinationFolder.exists()) {
            destinationFolder.mkdirs()
        }

        val entries = zipFile.entries()

        while (entries.hasMoreElements()) {
            val entry = entries.nextElement()
            val entryDestination = File(destinationFolderPath, entry.name)

            if (entry.isDirectory) {
                entryDestination.mkdirs()
            } else {
                entryDestination.parentFile.mkdirs()

                zipFile.getInputStream(entry).use { input ->
                    Files.copy(input, entryDestination.toPath(), StandardCopyOption.REPLACE_EXISTING)
                }
            }
        }
    }

    fun zip(sourceDirectory: File, zipFilePath: String) {
        val outputStream = ZipOutputStream(FileOutputStream(zipFilePath))

        sourceDirectory.walkTopDown().forEach { file ->
            val entryName = sourceDirectory.toPath().relativize(file.toPath()).toString()
            val entry = ZipEntry(entryName)
            outputStream.putNextEntry(entry)

            if (file.isFile) {
                Files.copy(file.toPath(), outputStream)
            }

            outputStream.closeEntry()
        }

        outputStream.close()
    }

}
