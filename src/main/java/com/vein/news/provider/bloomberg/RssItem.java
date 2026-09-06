package com.vein.news.provider.bloomberg;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** A single RSS 2.0 {@code <item>}. */
@JsonIgnoreProperties(ignoreUnknown = true)
record RssItem(String title, String link, String description, String pubDate, String guid) {
}
