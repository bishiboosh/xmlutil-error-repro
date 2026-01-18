import kotlinx.io.asSource
import kotlinx.io.buffered
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.devrieze.xmlutil.serialization.kxio.decodeFromSource
import nl.adaptivity.xmlutil.XmlDeclMode
import nl.adaptivity.xmlutil.serialization.XML
import nl.adaptivity.xmlutil.serialization.XmlChildrenName
import nl.adaptivity.xmlutil.serialization.XmlElement
import nl.adaptivity.xmlutil.serialization.XmlSerializationPolicy

fun main(args: Array<String>) {
    val xmlFileStream = Thread
        .currentThread()
        .contextClassLoader
        .getResourceAsStream("htmlunit-4.21.0.pom")
    DefaultXml.decodeFromSource<MavenInfo>(xmlFileStream!!.asSource().buffered())
}

val DefaultXml = XML.recommended {
    policy {
        autoPolymorphic = false
        ignoreUnknownChildren()
        encodeDefault = XmlSerializationPolicy.XmlEncodeDefault.NEVER
    }
    repairNamespaces = true
    xmlDeclMode = XmlDeclMode.None
    defaultToGenericParser = true
}

@Serializable
@SerialName("project")
internal data class MavenInfo(
    @XmlElement(true) val name: String? = null,
    @XmlElement(true) val url: String? = null,
    @XmlChildrenName("dependency") val dependencies: List<Dependency> = emptyList(),
    val scm: SCMInfos? = null,
)

@Serializable
@SerialName("dependency")
internal data class Dependency(
    @XmlElement(true) val groupId: String,
    @XmlElement(true) val artifactId: String,
    @XmlElement(true) val version: String? = null,
)

@Serializable
@SerialName("scm")
internal data class SCMInfos(
    @XmlElement(true) val url: String? = null,
)
