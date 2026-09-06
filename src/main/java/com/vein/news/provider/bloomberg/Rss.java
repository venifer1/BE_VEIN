package com.vein.news.provider.bloomberg;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Root {@code <rss>} element of an RSS 2.0 feed. */
@JsonIgnoreProperties(ignoreUnknown = true)
record Rss(RssChannel channel) {
}
