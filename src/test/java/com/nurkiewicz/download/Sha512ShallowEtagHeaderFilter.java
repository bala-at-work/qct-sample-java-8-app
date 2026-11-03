package com.nurkiewicz.download;

import com.google.common.hash.HashCode;
import com.google.common.hash.Hashing;
import org.springframework.web.filter.ShallowEtagHeaderFilter;

import java.io.IOException;
import java.io.InputStream;

public class Sha512ShallowEtagHeaderFilter extends ShallowEtagHeaderFilter {

	@Override
	protected String generateETagHeaderValue(InputStream inputStream, boolean isWeak) throws IOException {
		final HashCode hash = Hashing.sha512().hashBytes(inputStream.readAllBytes());
		return (isWeak ? "W/" : "") + "\"" + hash + "\"";
	}
}
