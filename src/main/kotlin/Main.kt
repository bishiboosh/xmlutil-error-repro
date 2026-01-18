import kotlinx.io.buffered
import kotlinx.io.okio.asKotlinxIoRawSource
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.devrieze.xmlutil.serialization.kxio.decodeFromSource
import nl.adaptivity.xmlutil.XmlDeclMode
import nl.adaptivity.xmlutil.serialization.XML
import nl.adaptivity.xmlutil.serialization.XmlChildrenName
import nl.adaptivity.xmlutil.serialization.XmlElement
import nl.adaptivity.xmlutil.serialization.XmlSerializationPolicy
import okhttp3.OkHttpClient
import okhttp3.Request

fun main(args: Array<String>) {
    OkHttpClient()
        .newCall(
            Request
                .Builder()
                .url("https://repo.maven.apache.org/maven2/org/htmlunit/htmlunit/4.21.0/htmlunit-4.21.0.pom")
                .build()
        )
        .execute()
        .body
        .source()
        .asKotlinxIoRawSource()
        .buffered()
        .use { source -> DefaultXml.decodeFromSource<MavenInfo>(source) }
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
