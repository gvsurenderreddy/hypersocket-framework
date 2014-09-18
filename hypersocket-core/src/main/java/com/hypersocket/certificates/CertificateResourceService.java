package com.hypersocket.certificates;

import java.io.UnsupportedEncodingException;
import java.security.KeyStore;
import java.util.Collection;
import java.util.Map;

import org.springframework.web.multipart.MultipartFile;

import com.hypersocket.certs.InvalidPassphraseException;
import com.hypersocket.permissions.AccessDeniedException;
import com.hypersocket.properties.PropertyCategory;
import com.hypersocket.realm.Realm;
import com.hypersocket.resource.AbstractResourceService;
import com.hypersocket.resource.ResourceChangeException;
import com.hypersocket.resource.ResourceCreationException;

public interface CertificateResourceService extends
		AbstractResourceService<CertificateResource> {

	CertificateResource updateResource(CertificateResource resourceById, String name, Map<String,String> properties)
			throws ResourceChangeException, AccessDeniedException;

	CertificateResource createResource(String name, Realm realm, Map<String,String> properties)
			throws ResourceCreationException, AccessDeniedException;

	Collection<PropertyCategory> getPropertyTemplate() throws AccessDeniedException;

	Collection<PropertyCategory> getPropertyTemplate(CertificateResource resource)
			throws AccessDeniedException;

	KeyStore getDefaultCertificate() throws ResourceCreationException, AccessDeniedException;

	String generateCSR(CertificateResource resourceById) throws UnsupportedEncodingException, Exception;

	void updateCertificate(CertificateResource resource, MultipartFile file,
			MultipartFile bundle) throws ResourceChangeException;

	void importPrivateKey(MultipartFile key, String passphrase,
			MultipartFile file, MultipartFile bundle)
			throws ResourceCreationException, InvalidPassphraseException;

}
