package com.flatironschool.javacs;

import java.io.IOException;
import java.util.LinkedList;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Queue;

import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.jsoup.select.Elements;

import redis.clients.jedis.Jedis;

public class WikiCrawler {
	// keeps track of where we started
	private final String source;

	// the index where the results go
	private JedisIndex index;

	// queue of URLs to be indexed
	private Queue<String> queue = new LinkedList<String>();

	private Queue<String> myqueue = new LinkedList<String>();

	// fetcher used to get pages from Wikipedia
	final static WikiFetcher wf = new WikiFetcher();

	/**
	 * Constructor.
	 * 
	 * @param source
	 * @param index
	 */
	public WikiCrawler(String source, JedisIndex index) {
		this.source = source;
		this.index = index;
		queue.offer(source);
	}

	/**
	 * Returns the number of URLs in the queue.
	 * 
	 * @return
	 */
	public int queueSize() {
		return queue.size();
	}

	/**
	 * Gets a URL from the queue and indexes it.
	 * 
	 * @param b
	 * 
	 * @return Number of pages indexed.
	 * @throws IOException
	 */
	public String crawl(boolean testing) throws IOException {
		// pop the first link from the queue
		String url = queue.poll();
		
		if (!testing && index.isIndexed(url))
			return null;

		Elements paragraphs = wf.readWikipedia(url);
		index.indexPage(url, paragraphs);

		queueInternalLinks(paragraphs);

		return url;

	}

	/**
	 * Parses paragraphs and adds internal links to the queue.
	 * 
	 * @param paragraphs
	 */
	// NOTE: absence of access level modifier means package-level
	void queueInternalLinks(Elements paragraphs) {

		System.out.println("***** Before adding links size of queue = " + queue.size());
		for (Element para : paragraphs) {
			try {
				addLinks(para);
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}

		System.out.println("**Size of queue = " + queue.size());

	}

	private void addLinks(Element para) throws IOException {

		Iterable<Node> iter = new WikiNodeIterable(para);

		for (Node node : iter) {

			if (node instanceof Element) {
				Element element = (Element) node;
				if (element.tagName().equals("a")) {
					String href = element.attr("href");

					if (queue.contains(href)) {
						System.out.println("Getting into a loop");
						throw new UnsupportedOperationException("Getting into a loop");
					} else if (href.startsWith("/wiki")) {

						String completeUrl = "https://en.wikipedia.org" + href;
						queue.add(completeUrl);
						// System.out.println("***Adding " + completeUrl);

					}

				}

			}

		}

	}

	public static void main(String[] args) throws IOException {

		// make a WikiCrawler
		Jedis jedis = JedisMaker.make();
		JedisIndex index = new JedisIndex(jedis);
		String source = "https://en.wikipedia.org/wiki/Java_(programming_language)";
		WikiCrawler wc = new WikiCrawler(source, index);

		// for testing purposes, load up the queue
		Elements paragraphs = wf.fetchWikipedia(source);
		wc.queueInternalLinks(paragraphs);

		// loop until we index a new page
		String res;
		do {
			res = wc.crawl(false);

			// REMOVE THIS BREAK STATEMENT WHEN crawl() IS WORKING
			break;
		} while (res == null);

		Map<String, Integer> map = index.getCounts("the");
		for (Entry<String, Integer> entry : map.entrySet()) {
			System.out.println(entry);
		}
	}
}
