import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter

class Data(
    @get:XmlJavaTypeAdapter(CollapsedStringAdapter::class)
    var value: String?
)