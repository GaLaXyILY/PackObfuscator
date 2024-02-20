package com.boy0000.pack_obfuscator.modelengine

import com.boy0000.pack_obfuscator.PackSquash
import com.boy0000.pack_obfuscator.obfuscator
import java.io.File

object ModelEnginePackSquash : PackSquash {
    override val inputDir = obfuscator.plugin.dataFolder.parentFile.resolve("ModelEngine/resource pack")
    override val outputZip = obfuscator.plugin.dataFolder.parentFile.resolve("ModelEngine/resource pack.zip")
}