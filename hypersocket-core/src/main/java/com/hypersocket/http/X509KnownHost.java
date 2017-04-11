package com.hypersocket.http;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.Arrays;

import javax.net.ssl.SSLSession;
import javax.security.cert.X509Certificate;

import org.bouncycastle.util.encoders.Base64;

import com.google.common.base.Objects;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public class X509KnownHost {

	private String hostname;
	private int port;
	private String algo;
	private byte[] sig;
	private String subject;
	private String detail;

	public X509KnownHost(String hostname, SSLSession session) {
		try {
			this.hostname = hostname;
			X509Certificate cert = session.getPeerCertificateChain()[0];
			sig = cert.getEncoded();
			port = session.getPeerPort();
			subject = cert.getSubjectDN().toString();
			algo = cert.getSigAlgName();
			detail = cert.toString();
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	public JsonObject toJSON() {
		/*
		 * Put all of the certificate properties we need to create a certificate
		 * entry into a string. This is passed to the client side javascript and
		 * back again
		 */
		String fp;
		try {
			fp = new String(Base64.encode(sig), "UTF-8");
		} catch (UnsupportedEncodingException e) {
			throw new RuntimeException(e);
		}
		JsonObject ob = new JsonObject();
		ob.addProperty("hostname", hostname);
		ob.addProperty("port", port);
		ob.addProperty("subject", subject);
		try {
			ob.addProperty("detail", URLEncoder.encode(detail, "UTF-8"));
		} catch (UnsupportedEncodingException e) {
			throw new RuntimeException(e);
		}
		ob.addProperty("sig", fp);
		ob.addProperty("algo", algo);
		return ob;
	}

	public X509KnownHost(String data) {
		JsonElement parse = new JsonParser().parse(data);
		JsonObject cert = parse.getAsJsonObject();
		algo = cert.get("algo").getAsString();
		hostname = cert.get("hostname").getAsString();
		try {
			detail = URLDecoder.decode(cert.get("detail").getAsString(), "UTF-8");
		} catch (UnsupportedEncodingException e) {
			throw new RuntimeException(e);
		}
		port = cert.get("port").getAsInt();
		subject = cert.get("subject").getAsString();
		sig = Base64.decode(cert.get("sig").getAsString());
	}
	
	public boolean matches(X509KnownHost h) {
		return Arrays.equals(sig, h.sig) && Objects.equal(hostname, h.hostname) && Objects.equal(port, h.port);
	}
	
	public boolean hostMatches(X509KnownHost h) {
		return Objects.equal(hostname, h.hostname) && Objects.equal(port, h.port);
	}

	public String getDetail() {
		return detail;
	}

	public String getHostname() {
		return hostname;
	}

	public String getAlgo() {
		return algo;
	}

	public int getPort() {
		return port;
	}

	public byte[] getSig() {
		return sig;
	}

	public String getSubject() {
		return subject;
	}

	public String toString() {
		return toJSON().toString();
	}

}