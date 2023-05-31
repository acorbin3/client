package org.runestar.client.updater.create

import com.fasterxml.jackson.annotation.JsonAutoDetect
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.PropertyAccessor
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.google.common.collect.Multimap
import com.google.common.collect.MultimapBuilder
import org.apache.maven.model.Resource
import org.apache.maven.plugin.AbstractMojo
import org.apache.maven.plugins.annotations.LifecyclePhase
import org.apache.maven.plugins.annotations.Mojo
import org.apache.maven.plugins.annotations.Parameter
import org.apache.maven.project.MavenProject
import org.objectweb.asm.Type
import org.runestar.client.common.JAV_CONFIG
import org.runestar.client.updater.common.ClassHook
import org.runestar.client.updater.common.ConstructorHook
import org.runestar.client.updater.common.FieldHook
import org.runestar.client.updater.common.MethodHook
import org.runestar.client.updater.deob.Transformer
import org.runestar.client.updater.deob.util.readClassNodes
import org.runestar.client.updater.deob.util.readClasses
import org.runestar.client.updater.deob.util.writeClasses
import org.runestar.client.updater.mapper.*
import org.runestar.client.updater.mapper.isPrimitive
import org.runestar.client.updater.mapper.std.classes.Client
import org.runestar.client.updater.mapper.Class2
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption

@Mojo(
        name = "create",
        defaultPhase = LifecyclePhase.GENERATE_RESOURCES
)
class CreateMojo : AbstractMojo() {

    @Parameter(defaultValue = "\${project}")
    lateinit var project: MavenProject

    private val jsonMapper = jacksonObjectMapper().setSerializationInclusion(JsonInclude.Include.NON_NULL)
            .setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.NONE)
            .setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY)

    private val targetDir: Path get() = Paths.get(project.build.directory)

    private val generatedResourcesDir: Path get() = targetDir.resolve("generated-resources")

    private val gamepackJar: Path get() = targetDir.resolve("gamepack.jar")

    private val deobJar: Path get() = targetDir.resolve("gamepack.deob.jar")

    private val cleanJar: Path get() = targetDir.resolve("gamepack.clean.jar")

    private val namesJson: Path get() = targetDir.resolve("names.json")

    private val opJson: Path get() = targetDir.resolve("op.json")

    private val opDescsJson: Path get() = targetDir.resolve("op-descs.json")

    private val multJson: Path get() = targetDir.resolve("mult.json")

    private val hooksJson: Path get() = targetDir.resolve("hooks.json")

    override fun execute() {
        println(gamepackJar)
        if (Files.notExists(gamepackJar)) {
            dl()
        }
        deob()
        clean()
        map()
        mergeHooks()

        addResources()
    }

    private fun dl() {

        //TODO -remember we usually get an issue that the target folder was not created. Just create it here:
        // C:\Users\C0rbin\Documents\GitHub\Runestar\client\updater\target
        // And that should fix the issues
        File("C:\\Users\\C0rbin\\Documents\\GitHub\\Runestar\\client\\updater\\target").mkdirs()
        println("URL download: ${JAV_CONFIG.gamepackUrl}")
        JAV_CONFIG.gamepackUrl.openStream().use { input ->
            Files.copy(input, gamepackJar, StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private fun deob() {
        val input = readClasses(gamepackJar)
        val output = Transformer.DEFAULT.transform(targetDir, input)
        writeClasses(output, deobJar)
    }

    private fun clean() {
        val input = readClasses(gamepackJar)
        val output = Transformer.CLEAN.transform(targetDir, input)
        writeClasses(output, cleanJar)
    }

    private fun map() {
        val ctx = Mapper.Context()
        val clientClass = Client::class.java
        JarMapper(clientClass.`package`.name, clientClass.classLoader).map(deobJar, ctx)
        val idClasses = if (project.properties.getProperty("runestar.placeholderhooks") == "true") {
            ctx.buildIdHierarchyAll()
        } else {
            ctx.buildIdHierarchy()
        }
        jsonMapper.writeValue(namesJson.toFile(), idClasses)
    }

    private fun Mapper.Context.buildIdHierarchyAll(): List<IdClass> {
        val identified = buildIdHierarchy().toMutableList()
        var i = 0
        val jar = classes.values.first().jar
        jar.classes.iterator().forEachRemaining { c ->
            if (classes.containsValue(c)) {
                c.instanceFields.filter { !fields.containsValue(it) }.forEach { f ->
                    if (!isTypeDenotable(f.type, classes.values)) return@forEach
                    val klass = identified.first { it.name == f.klass.name }
                    identified.remove(klass)
                    identified.add(klass.copy(fields = klass.fields.plus(IdField("__${f.name}", f.klass.name, f.name, f.access, f.desc))))
                }
                c.instanceMethods.filter { !methods.containsValue(it) }.iterator().forEach { m ->
                    if (!isTypeDenotable(m.returnType, classes.values)) return@forEach
                    if (m.arguments.any { !isTypeDenotable(it, classes.values) }) return@forEach
                    val klass = identified.first { it.name == m.klass.name }
                    identified.remove(klass)
                    val ps = m.arguments.mapIndexed { i, _ -> "arg$i" }
                    identified.add(klass.copy(methods = klass.methods.plus(IdMethod("__${m.name}_${i++}", m.klass.name, m.name, m.access, ps, m.desc))))
                }
            }
            c.staticFields.filter { !fields.containsValue(it) }.forEach { f ->
                if (!isTypeDenotable(f.type, classes.values)) return@forEach
                val klass = identified.first { it.name == "client" }
                identified.remove(klass)
                identified.add(klass.copy(fields = klass.fields.plus(IdField("__${f.klass.name}_${f.name}", f.klass.name, f.name, f.access, f.desc))))
            }
        }
        return identified
    }

    private fun isTypeDenotable(t: Type, classes: Collection<Class2>): Boolean {
        return when {
            t.isPrimitive -> true
            t.sort == Type.ARRAY -> isTypeDenotable(t.elementType, classes)
            else -> classes.any { it.name == t.internalName } || try { Class.forName(t.className); true } catch (e: Exception) { false }
        }
    }

    private fun findConstructors(): Multimap<String, ConstructorHook> {
        val classNodes = readClassNodes(deobJar)
        val opDescs = jsonMapper.readValue<Map<String, String>>(opDescsJson.toFile())
        val constructors = MultimapBuilder.hashKeys().arrayListValues().build<String, ConstructorHook>()
        classNodes.forEach { c ->
            c.methods.forEach { m ->
                if (m.name == "<init>") {
                    val desc = opDescs["${c.name}.${m.name}${m.desc}:${m.access}"] ?: m.desc
                    constructors.put(c.name, ConstructorHook(m.access, desc))
                }
            }
        }
        return constructors
    }

    private fun mergeHooks() {
        val ops = jsonMapper.readValue<Map<String, Int>>(opJson.toFile())
        val opDescs = jsonMapper.readValue<Map<String, String>>(opDescsJson.toFile())
        val mults = jsonMapper.readValue<Map<String, Long>>(multJson.toFile())
        val names = jsonMapper.readValue<List<IdClass>>(namesJson.toFile())
        val constructors = findConstructors()
        val hooks = names.map { c ->
            val fields = c.fields.map { f ->
                FieldHook(f.field, f.owner, f.name, f.access, f.descriptor, mults["${f.owner}.${f.name}"])
            }
            val methods = c.methods.map { m ->
                val desc = opDescs["${m.owner}.${m.name}${m.descriptor}:${m.access}"] ?: m.descriptor
                MethodHook(m.method, m.owner, m.name, m.access, m.parameters, desc, ops["${m.owner}.${m.name}$desc"])
            }
            val cons = constructors[c.name].toList()
            ClassHook(c.`class`, c.name, c.`super`, c.access, c.interfaces, fields, methods, cons)
        }
        jsonMapper.writeValue(hooksJson.toFile(), hooks)
    }

    private fun addResources() {
        Files.createDirectories(generatedResourcesDir)
        project.addResource(Resource().apply {
            directory = generatedResourcesDir.toString()
        })
        Files.copy(gamepackJar, generatedResourcesDir.resolve(gamepackJar.fileName), StandardCopyOption.REPLACE_EXISTING)
        Files.copy(cleanJar, generatedResourcesDir.resolve(cleanJar.fileName), StandardCopyOption.REPLACE_EXISTING)
        Files.copy(hooksJson, generatedResourcesDir.resolve(hooksJson.fileName), StandardCopyOption.REPLACE_EXISTING)
    }
}