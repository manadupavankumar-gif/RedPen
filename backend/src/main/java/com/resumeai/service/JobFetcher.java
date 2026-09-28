package com.resumeai.service;

import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

/**
 * Loads the text of a job posting from a web link.
 * Safety: only http/https on the normal ports, and every address (including redirects) must be a
 * public internet address, so this cannot be used to reach servers inside your own network.
 */
@Service
public class JobFetcher {

    private static final int MAX_CHARS = 5000;

    public String fetchText(String rawUrl) {
        if (rawUrl == null || rawUrl.isBlank()) throw bad("Paste a job link first.");
        URI uri;
        try {
            uri = new URI(rawUrl.strip());
        } catch (URISyntaxException e) {
            throw bad("That does not look like a valid link.");
        }

        URI current = uri;
        try {
            for (int hop = 0; hop < 4; hop++) {
                assertPublic(current);
                Connection.Response res = Jsoup.connect(current.toString())
                        .userAgent("Mozilla/5.0 (compatible; RedpenBot/1.0)")
                        .timeout(8000)
                        .maxBodySize(1_500_000)
                        .followRedirects(false)
                        .ignoreHttpErrors(true)
                        .ignoreContentType(true)
                        .execute();

                int code = res.statusCode();
                if (code >= 300 && code < 400) {
                    String loc = res.header("Location");
                    if (loc == null || loc.isBlank()) break;
                    current = current.resolve(loc);
                    continue;
                }
                if (code >= 400) {
                    throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                            "That site did not allow us to read the page (" + code + "). Paste the job text instead.");
                }
                String type = res.contentType() == null ? "" : res.contentType().toLowerCase();
                if (!type.contains("html") && !type.contains("text")) {
                    throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                            "That link is not a web page. Paste the job text instead.");
                }
                return extract(res.parse());
            }
        } catch (ResponseStatusException e) {
            throw e;
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Could not open that link. Check it, or paste the job text instead.");
        }
        throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                "That link redirects too many times. Paste the job text instead.");
    }

    private String extract(Document doc) {
        doc.select("script,style,nav,footer,header,noscript,svg,form,iframe").remove();
        List<String> parts = new ArrayList<>();
        for (Element el : doc.select("h1,h2,h3,h4,p,li")) {
            String t = el.text().strip();
            if (t.length() > 2) parts.add(t);
        }
        String text = String.join("\n", parts);
        if (text.length() < 200 && doc.body() != null) text = doc.body().text();
        text = text.strip();
        if (text.length() < 200) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Could not read a job description from that page. Many job sites load their content with scripts or block "
                            + "bots (LinkedIn and Indeed usually do). Paste the job text instead.");
        }
        return text.length() > MAX_CHARS ? text.substring(0, MAX_CHARS) : text;
    }

    private void assertPublic(URI u) {
        String scheme = u.getScheme() == null ? "" : u.getScheme().toLowerCase();
        if (!scheme.equals("http") && !scheme.equals("https")) throw bad("Only http and https links are allowed.");
        int port = u.getPort();
        if (port != -1 && port != 80 && port != 443) throw bad("That link uses a port that is not allowed.");
        String host = u.getHost();
        if (host == null || host.isBlank()) throw bad("That does not look like a valid link.");
        try {
            for (InetAddress a : InetAddress.getAllByName(host)) {
                if (isPrivate(a)) throw bad("That address is not allowed.");
            }
        } catch (java.net.UnknownHostException e) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Could not find that website.");
        }
    }

    private static boolean isPrivate(InetAddress a) {
        if (a.isAnyLocalAddress() || a.isLoopbackAddress() || a.isLinkLocalAddress()
                || a.isSiteLocalAddress() || a.isMulticastAddress()) return true;
        byte[] b = a.getAddress();
        if (b.length == 4) {
            int b0 = b[0] & 0xFF;
            int b1 = b[1] & 0xFF;
            return b0 == 0 || (b0 == 100 && b1 >= 64 && b1 <= 127); // 0.0.0.0/8 and carrier-grade NAT
        }
        return b.length == 16 && (b[0] & 0xFE) == 0xFC; // IPv6 unique local fc00::/7
    }

    private static ResponseStatusException bad(String msg) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, msg);
    }
}
