package com.vein.news.provider.bloomberg;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;

/** {@code <channel>} holding repeated {@code <item>} entries (unwrapped). */
@JsonIgnoreProperties(ignoreUnknown = true)
record RssChannel(
        @JacksonXmlProperty(localName = "item")
        @JacksonXmlElementWrapper(useWrapping = false)
        List<RssItem> items) {
}
