package dls.bean;

import lombok.Builder;
import lombok.Data;

import java.util.List;

public @Data @Builder class Catalog {

	private String page;
	private Integer count;
	private List <FileDetail> result;
}
