import kotlinx.io.asSource
import kotlinx.io.buffered
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.encoding.decodeStructure
import kotlinx.serialization.encoding.encodeStructure
import kotlinx.serialization.serializer
import net.devrieze.xmlutil.serialization.kxio.decodeFromSource
import nl.adaptivity.xmlutil.EventType
import nl.adaptivity.xmlutil.ExperimentalXmlUtilApi
import nl.adaptivity.xmlutil.XmlDeclMode
import nl.adaptivity.xmlutil.XmlDelegatingReader
import nl.adaptivity.xmlutil.XmlException
import nl.adaptivity.xmlutil.XmlReader
import nl.adaptivity.xmlutil.XmlSerializer
import nl.adaptivity.xmlutil.XmlWriter
import nl.adaptivity.xmlutil.serialization.InputKind
import nl.adaptivity.xmlutil.serialization.XML
import nl.adaptivity.xmlutil.serialization.XmlChildrenName
import nl.adaptivity.xmlutil.serialization.XmlElement
import nl.adaptivity.xmlutil.serialization.XmlSerialName
import nl.adaptivity.xmlutil.serialization.XmlSerializationPolicy
import nl.adaptivity.xmlutil.serialization.XmlValue
import nl.adaptivity.xmlutil.serialization.structure.XmlDescriptor

fun main(args: Array<String>) {
    val xmlFileStream = Thread
        .currentThread()
        .contextClassLoader
        .getResourceAsStream("htmlunit-4.21.0.pom")
    DefaultXml.decodeFromSource<MavenInfo>(xmlFileStream!!.asSource().buffered())
}

val DefaultXml = XML {
    defaultPolicy {
        ignoreUnknownChildren()
        encodeDefault = XmlSerializationPolicy.XmlEncodeDefault.NEVER
    }
    repairNamespaces = true
    xmlDeclMode = XmlDeclMode.None
    autoPolymorphic = false
}

@Serializable(MavenInfoSerializer::class)
internal data class MavenInfo(
    val name: String? = null,
    val url: String? = null,
    val dependencies: List<Dependency> = emptyList(),
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

internal class DynamicTagReader(
    private val idPropertyName: String,
    reader: XmlReader,
    descriptor: XmlDescriptor
) : XmlDelegatingReader(reader) {

    private val filterDepth = delegate.depth - reader.depth

    private val elementName = descriptor.tagName

    private val idAttrName = (0 until descriptor.elementsCount)
        .first { descriptor.serialDescriptor.getElementName(it) == idPropertyName }
        .let { descriptor.getElementDescriptor(it) }
        .tagName

    private val idValue = delegate.localName

    override val attributeCount: Int
        get() = if (filterDepth == 0) super.attributeCount + 1 else super.attributeCount

    override fun getAttributeNamespace(index: Int): String = if (filterDepth == 0) {
        if (index == 0) idAttrName.namespaceURI else super.getAttributeNamespace(index - 1)
    } else {
        super.getAttributeNamespace(index)
    }

    override fun getAttributePrefix(index: Int): String = if (filterDepth == 0) {
        if (index == 0) idAttrName.prefix else super.getAttributePrefix(index - 1)
    } else {
        super.getAttributePrefix(index)
    }

    override fun getAttributeLocalName(index: Int): String = if (filterDepth == 0) {
        if (index == 0) idAttrName.localPart else super.getAttributeLocalName(index - 1)
    } else {
        super.getAttributeLocalName(index)
    }

    override fun getAttributeValue(index: Int): String = if (filterDepth == 0) {
        if (index == 0) idValue else super.getAttributeValue(index - 1)
    } else {
        super.getAttributeValue(index)
    }

    @Suppress("UnnecessaryParentheses") // More understandable this way
    override fun getAttributeValue(nsUri: String?, localName: String): String? =
        if (
            filterDepth == 0 &&
            nsUri.orEmpty() == idAttrName.namespaceURI &&
            localName == idAttrName.localPart
        ) {
            idValue
        } else {
            super.getAttributeValue(nsUri, localName)
        }

    override val namespaceURI: String
        get() = if (filterDepth == 0) elementName.namespaceURI else super.namespaceURI

    override val localName: String
        get() = if (filterDepth == 0) elementName.localPart else super.localName

    override val prefix: String
        get() = if (filterDepth == 0) elementName.prefix else super.prefix
}

@Serializable
@SerialName("project")
private data class RawMavenInfo(
    @XmlElement val name: String? = null,
    @XmlElement val url: String? = null,
    @XmlChildrenName("dependency") val dependencies: List<Dependency> = emptyList(),
    @XmlElement @XmlSerialName("scm") val scm: SCMInfos? = null,
    @XmlElement
    @XmlSerialName("properties")
    @Serializable(PropertiesMapSerializer::class)
    val properties: Map<String, String> = emptyMap(),
) {
    constructor(mavenInfo: MavenInfo) : this(
        name = mavenInfo.name,
        url = mavenInfo.url,
        dependencies = mavenInfo.dependencies,
        scm = mavenInfo.scm,
    )

    fun resolved(): MavenInfo {
        return if (properties.isEmpty()) {
            return MavenInfo(
                name = name,
                url = url,
                dependencies = dependencies,
                scm = scm,
            )
        } else {
            MavenInfo(
                name = name?.resolve(properties),
                url = url?.resolve(properties),
                dependencies = dependencies.map { dependency ->
                    Dependency(
                        groupId = dependency.groupId.resolve(properties),
                        artifactId = dependency.artifactId.resolve(properties),
                        version = dependency.version?.resolve(properties),
                    )
                },
                scm = scm?.let { scmInfos ->
                    SCMInfos(
                        url = scmInfos.url?.resolve(properties)
                    )
                }
            )
        }
    }

    companion object {
        private fun String.resolve(properties: Map<String, String>): String {
            return PLACEHOLDER_REGEX.replace(this) { matchResult ->
                properties[matchResult.groupValues[1]] ?: matchResult.value
            }
        }

        private val PLACEHOLDER_REGEX = "\\$\\{(.+?)}".toRegex()
    }
}

internal object MavenInfoSerializer : KSerializer<MavenInfo> {

    private val rawSerializer = serializer<RawMavenInfo>()

    override val descriptor: SerialDescriptor
        get() = rawSerializer.descriptor

    override fun serialize(
        encoder: Encoder,
        value: MavenInfo
    ) {
        encoder.encodeSerializableValue(rawSerializer, RawMavenInfo(value))
    }

    override fun deserialize(decoder: Decoder): MavenInfo {
        return decoder.decodeSerializableValue(rawSerializer).resolved()
    }
}

@Serializable(with = PropertiesSerializer::class)
private data class Properties(val properties: List<Property> = emptyList())

@Serializable
private data class Property(val key: String, @XmlValue val value: String)

private object PropertiesSerializer : XmlSerializer<Properties> {

    private val elementSerializer = serializer<Property>()

    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("Properties") {
        element("properties", ListSerializer(elementSerializer).descriptor)
    }

    override fun deserialize(decoder: Decoder): Properties {
        val properties = decoder.decodeStructure(descriptor) {
            decodeSerializableElement(descriptor, 0, ListSerializer(elementSerializer))
        }
        return Properties(properties)
    }

    override fun serialize(
        encoder: Encoder,
        value: Properties
    ) {
        encoder.encodeStructure(descriptor) {
            encodeSerializableElement(
                descriptor = descriptor,
                index = 0,
                serializer = ListSerializer(elementSerializer),
                value = value.properties
            )
        }
    }

    override fun deserializeXML(
        decoder: Decoder,
        input: XmlReader,
        previousValue: Properties?,
        isValueChild: Boolean
    ): Properties {
        val xml = (decoder as XML.XmlInput).delegateFormat()

        val elementXmlDescriptor = xml
            .xmlDescriptor(elementSerializer)
            .getElementDescriptor(0)

        val propertyList = mutableListOf<Property>()
        decoder.decodeStructure(descriptor) {
            while (input.next() != EventType.END_ELEMENT) {
                when (input.eventType) {
                    EventType.COMMENT,
                    EventType.IGNORABLE_WHITESPACE -> {
                        // Comments and whitespace are just ignored
                    }

                    EventType.ENTITY_REF,
                    EventType.TEXT -> {
                        if (input.text.isNotBlank()) {
                            @OptIn(ExperimentalXmlUtilApi::class)
                            xml.config.policy.handleUnknownContentRecovering(
                                input = input,
                                inputKind = InputKind.Text,
                                descriptor = elementXmlDescriptor,
                                name = null,
                                candidates = emptyList()
                            )
                        }
                    }

                    EventType.START_ELEMENT -> {
                        val filter = DynamicTagReader(
                            idPropertyName = "key",
                            reader = input,
                            descriptor = elementXmlDescriptor
                        )

                        val property = xml.decodeFromReader(elementSerializer, filter)
                        propertyList.add(property)
                    }

                    else -> // other content that shouldn't happen
                        throw XmlException("Unexpected tag content")
                }
            }
        }
        return Properties(propertyList)
    }

    override fun serializeXML(
        encoder: Encoder,
        output: XmlWriter,
        value: Properties,
        isValueChild: Boolean
    ) {
        error("PropertiesSerializer serialization is not supported")
    }
}

internal object PropertiesMapSerializer : KSerializer<Map<String, String>> {

    private val propertiesSerializer = PropertiesSerializer

    override val descriptor: SerialDescriptor
        get() = propertiesSerializer.descriptor

    override fun serialize(
        encoder: Encoder,
        value: Map<String, String>
    ) {
        error("PropertiesMapSerializer serialization is not supported")
    }

    override fun deserialize(decoder: Decoder): Map<String, String> {
        return decoder
            .decodeSerializableValue(propertiesSerializer)
            .properties
            .associate { it.key to it.value }
    }
}
