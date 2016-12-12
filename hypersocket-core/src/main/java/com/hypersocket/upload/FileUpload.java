package com.hypersocket.upload;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Table;
import javax.persistence.Transient;

import com.hypersocket.resource.RealmResource;

@Entity
@Table(name = "uploaded_files")
public class FileUpload extends RealmResource {

	private static final long serialVersionUID = 8896410295739619520L;

	@Column(name = "file_name")
	String fileName;

	@Column(name = "md5_sum")
	String md5Sum;

	@Column(name = "file_size")
	Long fileSize;

	@Column(name = "type")
	String type;
	
	@Transient
	String url;
	
	@Column(name="short_code", nullable=true)
	String shortCode;

	@Transient
	String content;
	
	public FileUpload() {
	}

	public String getFileName() {
		return fileName;
	}

	public void setFileName(String fileName) {
		this.fileName = fileName;
	}

	public String getMd5Sum() {
		return md5Sum;
	}

	public void setMd5Sum(String md5Sum) {
		this.md5Sum = md5Sum;
	}

	public Long getFileSize() {
		return fileSize;
	}

	public void setFileSize(Long fileSize) {
		this.fileSize = fileSize;
	}

	public String getType() {
		return type;
	}

	public void setType(String type) {
		this.type = type;
	}
	
	void setUrl(String url) {
		this.url = url;
	}
	
	public String getUrl() {
		return url;
	}

	public void setShortCode(String shortCode) {
		this.shortCode = shortCode;
	}
	
	public String getShortCode() {
		return shortCode;
	}

	public String getContent() {
		return content;
	}

	public void setContent(String content) {
		this.content = content;
	}
}
