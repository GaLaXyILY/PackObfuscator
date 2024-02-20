package com.boy0000.pack_obfuscator.modelengine

import com.boy0000.pack_obfuscator.CreativeObfuscator
import com.boy0000.pack_obfuscator.obfuscator
import com.mineinabyss.idofront.messaging.logInfo
import com.mineinabyss.idofront.messaging.logSuccess
import com.ticxo.modelengine.api.events.ModelRegistrationEvent
import com.ticxo.modelengine.api.generator.ModelGenerator
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener

class ModelEngineListener : Listener {

    private val megZipped = obfuscator.plugin.dataFolder.parentFile.resolve("ModelEngine/resource pack.zip")

    @EventHandler(priority = EventPriority.HIGHEST)
    fun ModelRegistrationEvent.onModelEnginePack() {
        if (phase != ModelGenerator.Phase.FINISHED) return
        if (obfuscator.config.modelEngine.obfuscate) {
            logInfo("Attempting to Obfuscate ModelEnginePack...")
            CreativeObfuscator.obfuscate(megZipped, megZipped.toPath())
            logSuccess("Successfully Obfuscated ModelEnginePack!")
        }
        if (obfuscator.config.packSquash.enabled) {
            logInfo("Running ModelEnginePack through PackSquash...")
            ModelEnginePackSquash.extractPackSquashFiles()
            ModelEnginePackSquash.squashPack()
            logSuccess("Successfully Squashed ModelEnginePack!")
        }
    }
}