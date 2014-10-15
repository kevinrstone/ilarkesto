/*
 * Copyright 2011 Witoslaw Koczewsi <wi@koczewski.de>
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU Affero
 * General Public License as published by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the
 * implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License along with this program. If not,
 * see <http://www.gnu.org/licenses/>.
 */
package ilarkesto.integration.testde;

import ilarkesto.core.auth.LoginData;
import ilarkesto.core.base.OperationObserver;
import ilarkesto.core.base.Parser;
import ilarkesto.core.base.Parser.ParseException;
import ilarkesto.core.base.Str;
import ilarkesto.core.html.Html;
import ilarkesto.core.logging.Log;
import ilarkesto.core.time.Date;
import ilarkesto.io.IO;
import ilarkesto.net.ApacheHttpDownloader;
import ilarkesto.net.HttpDownloader;

import java.io.File;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class TestDe {

	private static final Log log = Log.get(TestDe.class);

	public static final String URL_BASE = "https://www.test.de";
	public static final String URL_TEST_INDEX = URL_BASE + "/tests/";
	public static final String URL_LOGIN = URL_BASE + "/meintest/login/default.ashx";
	public static final String URL_LOGOUT = URL_BASE + "/service/logout/";

	public static HttpDownloader http = new ApacheHttpDownloader();
	private static final String charset = IO.UTF_8;

	private static final DateFormat dateFormat = new SimpleDateFormat("dd.MM.yyyy", Locale.GERMANY);

	public static void downloadPdf(SubArticleRef subArticlePdf, ArticleRef articleRef, File file,
			OperationObserver observer) throws ParseException {
		if (subArticlePdf.isLocked()) throw new IllegalStateException("Article is locked: " + subArticlePdf);
		if (!subArticlePdf.isPdf()) throw new IllegalStateException("Article is not PDF: " + subArticlePdf);
		String url = loadPdfUrl(subArticlePdf, articleRef, observer);
		observer.onOperationInfoChanged(OperationObserver.DOWNLOADING, url);
		http.downloadUrlToFile(url, file);
	}

	private static String loadPdfUrl(SubArticleRef subArticlePdf, ArticleRef articleRef, OperationObserver observer)
			throws ParseException {
		String data = downloadPageHtml(articleRef.getPageRef(), observer);
		Parser parser = new Parser(data);
		parser.gotoTo("/filestore/" + subArticlePdf.getPageRef());
		String path = parser.getUntil("\"");
		path = path.replace("&amp;", "&");
		return URL_BASE + path;
	}

	public static void login(LoginData loginData, OperationObserver observer) {
		logout(observer);
		log.info("Login as", loginData.getLogin());
		observer.onOperationInfoChanged(OperationObserver.DOWNLOADING, URL_LOGIN);
		Map<String, String> params = new HashMap<String, String>();
		params.put("username", loginData.getLogin());
		params.put("password", loginData.getPassword());
		params.put("source", "login");
		params.put("autologin", "on");
		String data = http.post(URL_LOGIN, params, charset);
		String error = Str.cutFromTo(data, "<div class=\"msg error\"", "</div>");
		if (error != null) {
			if (error.contains("<p>")) error = Str.cutFromTo(error, "<p>", "</p>");
			throw new RuntimeException(error);
		}
		if (!data.contains("/service/logout/")) {
			log.warn("Logged in indicator 'logout=true' missing:", data);
			throw new RuntimeException("Login failed. Missing indicator '/service/logout/'");
		}
	}

	public static void logout(OperationObserver observer) {
		http.downloadText(URL_LOGOUT, charset, 1);
	}

	public static String removeSpamFromPageHtml(String html) {
		if (Str.isBlank(html)) return null;

		String beginIndicator = "<h2>";
		String endIndicator = "<div id=\"secondary\"";

		if (html.contains("<div class=\"ct-products\">")) {
			beginIndicator = "<div class=\"ct-products\">";
		} else if (html.contains("<div class=\"ct-wrapper\"")) {
			beginIndicator = "<div class=\"ct-wrapper\">";
		} else if (html.contains("<div id=\"primary\" class=\"l-primary\">")) {
			beginIndicator = "<div id=\"primary\" class=\"l-primary\">";
		}

		if (html.contains("<!--CT ProductComparisonPromoControl")) {
			endIndicator = "<!--CT ProductComparisonPromoControl";
		} else if (html.contains("<div class=\"articlepage-next\"")) {
			endIndicator = "<div class=\"articlepage-next\">";
		} else if (html.contains("<div class=\"paymentbox\" id=\"payment\"")) {
			endIndicator = "<div class=\"paymentbox\" id=\"payment\"";
		} else if (html.contains("<div id=\"ugc\">")) {
			endIndicator = "<div id=\"ugc\">";
		}

		html = beginIndicator + Str.cutFromTo(html, beginIndicator, endIndicator);

		html = Str.removeCenter(html, "<div class=\"paymentbox\"", "\n</div>");
		html = Str.removeCenter(html, "<div class=\"articlepage-next\"", "</div>");
		html = Str.removeCenter(html, "<div class=\"productlist-header\"", "</div>");
		html = Str.removeCenter(html, "<div class=\"productlist-footer\"", "</div>");
		html = Str.removeCenter(html, "<div id=\"mp3content", "</div>");
		html = Str.removeCenter(html, "<p class=\"back\"", "</p>");
		html = Str.removeCenters(html, "<div class=\"product-compare\">", "</div></div></div>");
		html = Str.removeCenters(html, "<div class=\"product-comparison ", "</div>");
		return html;
	}

	public static Article downloadArticle(ArticleRef ref, OperationObserver observer) throws ParseException {
		String data = downloadPageHtml(ref.getPageRef(), observer);
		return parseArticle(ref, data);
	}

	public static Article parseArticle(ArticleRef ref, String html) throws ParseException {
		String navigData = Str.cutFromTo(html, "<ol class=\"articlenav-nav\">", "</ol>");
		List<SubArticleRef> subArticles = new ArrayList<TestDe.SubArticleRef>();
		if (navigData != null) {
			Parser parser = new Parser(navigData);
			parser.skipWhitespace();
			while (parser.gotoAfterIf("<li")) {
				parser.gotoAfter("<a ");
				parser.gotoAfter("href=\"");
				String pageRef;
				pageRef = parser.getUntil("\"");
				pageRef = Str.removePrefix(pageRef, "/");
				pageRef = Str.removePrefix(pageRef, "filestore/");
				pageRef = Str.removeSuffix(pageRef, "/");
				if (pageRef.contains("?")) pageRef = pageRef.substring(0, pageRef.indexOf('?'));
				parser.gotoAfter(">");
				if (parser.gotoAfterIfNext("<i")) {
					parser.gotoAfter("</i>");
				}
				String title = parser.getUntil("<");
				title = Html.convertHtmlToText(title);
				title = title.replace("­", "");
				parser.gotoAfter("</li>");

				SubArticleRef subArticleRef = new SubArticleRef(title, pageRef);
				subArticles.add(subArticleRef);
			}
		}

		Parser parser = new Parser(html);
		parser.gotoAfter("<div id=\"primary\"");
		parser.gotoAfter("<p");
		parser.gotoAfter(">");
		String summary = parser.getUntil("</p>");
		// String summary = Str.cutFromTo(data, "<p class=\"intro\">", "</p>");
		summary = Html.convertHtmlToText(summary);

		return new Article(ref, subArticles, summary);
	}

	public static String getArticleUrl(ArticleRef ref) {
		return getPageUrl(ref.getPageRef());
	}

	public static String getSubArticleUrl(SubArticleRef ref) {
		return getPageUrl(ref.getPageRef());
	}

	public static String getPageUrl(String pageRef) {
		if (pageRef.contains("key=")) return URL_BASE + "/" + pageRef;
		return URL_BASE + "/" + pageRef + "/";
	}

	public static List<String> downloadPageHtmlWithMultipage(String pageRef, OperationObserver observer) {
		List<String> ret = new ArrayList<String>();
		Integer start = 1;
		while (start != null) {
			String html = downloadPageHtml(pageRef, start, observer);
			ret.add(html);
			start = parseNextPageStartOffset(html);
		}
		return ret;
	}

	public static String downloadPageHtml(String pageRef, OperationObserver observer) {
		return downloadPageHtml(pageRef, 1, observer);
	}

	public static String downloadPageHtml(String pageRef, Integer startOffset, OperationObserver observer) {
		String url = TestDe.getPageUrl(pageRef);
		if (startOffset != null) url += "?start=" + startOffset;
		observer.onOperationInfoChanged(OperationObserver.DOWNLOADING, url);
		return http.downloadText(url, charset);
	}

	public static Integer parseNextPageStartOffset(String html) {
		String nav = Str.cutFromTo(html, "<a class=\"fwd\"", "</a>");
		if (nav == null) return null;
		String start = Str.cutFromTo(nav, "start=", "\"");
		if (start == null) return null;
		return Integer.parseInt(start);
	}

	public static List<ArticleRef> update(ArticlesIndex index, OperationObserver observer) throws ParseException {
		List<ArticleRef> newArticles = downloadNewArticleRefs(index.getArticles(), observer);
		if (!newArticles.isEmpty()) index.addNewArticles(newArticles);
		return newArticles;
	}

	static List<ArticleRef> downloadNewArticleRefs(Collection<ArticleRef> knownArticles, OperationObserver observer)
			throws ParseException {
		Set<String> knownArticleIds = new HashSet<String>();
		Date newest = new Date(1999, 1, 1);
		for (ArticleRef articleRef : knownArticles) {
			if (articleRef.getDate().isAfter(newest)) newest = articleRef.getDate();
			knownArticleIds.add(articleRef.getPageId());
		}
		log.info("Downloading new articles. Known:", knownArticleIds.size(), "->", knownArticleIds);
		List<ArticleRef> ret = new ArrayList<TestDe.ArticleRef>();
		int offset = 1;
		Date deadline = newest.addMonths(-1);
		while (true) {
			List<ArticleRef> newArticles = downloadArticleRefs(offset, observer);
			if (newArticles.isEmpty()) {
				log.info("No articles on page", offset);
				return ret;
			}
			for (ArticleRef ref : newArticles) {
				if (ref.getDate().isBefore(deadline)) {
					log.info("Deadline reached on page", offset, "->", deadline);
					return ret;
				}
				if (ret.contains(ref)) {
					log.info("Last page reached:", offset);
					return ret;
				}
				if (knownArticleIds.contains(ref.getPageId())) continue;
				ret.add(ref);
			}
			offset++;
		}
	}

	static List<ArticleRef> downloadArticleRefs(int indexOffset, OperationObserver observer) throws ParseException {
		if (indexOffset == 0) throw new IllegalArgumentException("page 0 does not exist");
		String url = URL_TEST_INDEX + "?seite=" + indexOffset;
		observer.onOperationInfoChanged(OperationObserver.DOWNLOADING, url);
		String data = http.downloadText(url, charset, 0);

		ArrayList<ArticleRef> ret = new ArrayList<ArticleRef>();
		Parser parser = new Parser(data);
		parser.gotoAfter("id=\"primary\"");
		parser.gotoAfter(">");
		if (!parser.isNext("<div class=\"themenliste-alle__list\"")) return ret;
		parser.gotoAfter("<ul>");
		while (parser.gotoAfterIfNext("<li")) {
			parser.gotoAfter("<a href=\"/");
			String pageRef = parser.getUntil("/\"");
			parser.gotoAfter("title=\"");
			String title = parser.getUntil("\"");
			title = Html.convertHtmlToText(title);
			parser.gotoAfter("<em class=\"date\">");
			String dateS = parser.getUntil("</span>");
			Date date;
			try {
				date = new Date(dateFormat.parse(dateS));
			} catch (java.text.ParseException ex) {
				throw new ParseException("Unexpected date format: " + dateS);
			}
			parser.gotoAfter("</li>");

			if (title.contains("Historischer Test")) continue;
			ArticleRef articleRef = new ArticleRef(date, title, pageRef);
			ret.add(articleRef);
		}

		return ret;
	}

	public static class Article {

		private ArticleRef ref;
		private String summary;
		// private String imageUrl;
		// private String pdfUrl;
		// private String videoUrl;
		private List<SubArticleRef> subArticles;

		public Article(ArticleRef ref, List<SubArticleRef> subArticles, String summary) {
			super();
			this.ref = ref;
			this.subArticles = subArticles;
			this.summary = summary;
		}

		public String getSummary() {
			return summary;
		}

		public boolean containsLockedSubArticles() {
			for (SubArticleRef sub : getSubArticles()) {
				if (sub.isLocked()) return true;
			}
			return false;
		}

		public List<SubArticleRef> getSubArticles() {
			return subArticles;
		}

		public ArticleRef getRef() {
			return ref;
		}

		public String getUrl() {
			return getArticleUrl(ref);
		}

		@Override
		public int hashCode() {
			return ref.hashCode();
		}

		@Override
		public boolean equals(Object obj) {
			if (!(obj instanceof Article)) return false;
			return ref.equals(((Article) obj).ref);
		}

		@Override
		public String toString() {
			return subArticles.size() + " in " + ref.toString();
		}

	}

	public static class SubArticleRef {

		private String title;
		private String pageRef;
		private String pageId;

		public SubArticleRef(String title, String pageRef) {
			super();
			this.title = title;
			this.pageRef = pageRef;

			pageId = pageRef;
			pageId = pageId.substring(pageId.lastIndexOf('-') + 1);
		}

		public SubArticleRef() {}

		public boolean isLocked() {
			return pageRef.startsWith("#");
		}

		public String getTitle() {
			return title;
		}

		public String getPageRef() {
			return pageRef;
		}

		public String getPageId() {
			return pageId;
		}

		public String getUrl() {
			return getSubArticleUrl(this);
		}

		@Override
		public String toString() {
			return getPageId() + " " + getTitle();
		}

		public boolean isPdf() {
			if (isLocked()) return false;
			return getPageRef().contains(".pdf");
		}
	}

	public static class ArticlesIndex {

		private List<ArticleRef> articles = new ArrayList<ArticleRef>();

		public List<ArticleRef> getArticles() {
			return articles;
		}

		public void addNewArticles(List<ArticleRef> newArticles) {
			articles.addAll(0, newArticles);
		}

		public ArticleRef getLastKnownArticle() {
			if (articles.isEmpty()) return null;
			return articles.get(0);
		}

		@Override
		public String toString() {
			return articles.size() + " articles";
		}

		public Object getArticlesCount() {
			if (articles == null) return 0;
			return articles.size();
		}

	}

	public static class ArticleRef implements Comparable<ArticleRef> {

		private String title;
		private Date date;
		private String pageRef;
		private String pageId;

		private transient String titleMainPart;
		private transient String titleSubPart;

		public ArticleRef(Date date, String title, String pageRef) {
			super();
			this.date = date;
			this.title = title;
			this.pageRef = pageRef;

			pageId = pageRef;
			pageId = Str.removeSuffix(pageId, "-0");
			if (pageId.contains("-")) {
				pageId = pageId.substring(pageId.lastIndexOf('-') + 1);
			}
		}

		public ArticleRef() {}

		@Deprecated
		public void repairTitle() {
			if (title != null && title.contains("<span")) title = Html.convertHtmlToText(title);
		}

		public String getUrl() {
			return getArticleUrl(this);
		}

		public String getTitleMainPart() {
			if (titleMainPart == null) {
				int idx = title.indexOf(": ");
				if (idx < 0) return title;
				titleMainPart = title.substring(0, idx);
			}
			return titleMainPart;
		}

		public String getTitleSubPart() {
			if (titleSubPart == null) {
				int idx = title.indexOf(": ");
				if (idx < 0) return "";
				titleSubPart = title.substring(idx + 2);
			}
			return titleSubPart;
		}

		public Date getDate() {
			return date;
		}

		public String getTitle() {
			return title;
		}

		public String getPageRef() {
			return pageRef;
		}

		public String getPageId() {
			return pageId;
		}

		@Override
		public int compareTo(ArticleRef other) {
			return other.getDate().compareTo(getDate());
		}

		@Override
		public int hashCode() {
			return pageId.hashCode();
		}

		@Override
		public boolean equals(Object obj) {
			if (!(obj instanceof ArticleRef)) return false;
			return pageId.equals(((ArticleRef) obj).pageId);
		}

		@Override
		public String toString() {
			return getDate() + " " + getTitle();
		}
	}

}
