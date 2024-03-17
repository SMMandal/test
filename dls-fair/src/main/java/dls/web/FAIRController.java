package dls.web;

import com.google.common.collect.Sets;
import dls.bean.*;
import dls.exception.DlsNotFoundException;
import dls.exception.DlsPrivacyException;
import dls.exception.DlsSecurityException;
import dls.exception.DlsValidationException;
import dls.service.FAIRService;
import dls.service.FAIRServiceHelper;
import dls.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import java.awt.event.ActionEvent;
import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static dls.bean.CatalogQuery.META_QUERY_REGEX;

@RestController

@Validated
public class FAIRController {

	@Autowired private UserService uservice;
	@Autowired private FAIRService fairService;

	private final static String URI_BASE = "/datapoint";

	@Tag(description="Create and get datapoint repository", name="Repository")
	@Operation( summary="Create a new repository node in data point",
			description = "Assign permissions to users for the directory. Following table describes how permission can be set :" +
					"<table><thead>" +
					"<tr><th><code>Permission</code></th><th><code>Description</code></th></tr></thead>" +
					"<tbody>" +
					"<tr><td><code>R</code></td><td>User can view the repository metadata and catalog links</td></tr>" +
					"<tr><td><code>W</code></td><td>User can create catalog in this repository and change repository metadata</td></tr>" +
					"<tr><td><code>D</code></td><td>User can delete this repository</td></tr>" +
					"</tbody></table>" )
	@PostMapping(value=URI_BASE + "/repository")
	public ResponseEntity <List<DlsResponse>> createRepository(
			@Parameter(hidden = true)
			@RequestHeader(value="x-api-key", required=false)  String apiKey,
			@Parameter(hidden = true)
			@RequestHeader(value="x-dls-key", required=false)  String dlsKey,
			@RequestBody List<@Valid FAIRRepositoryDescriptor> repositories
			) throws DlsPrivacyException, DlsSecurityException, DlsNotFoundException {

		return ResponseEntity.status(HttpStatus.MULTI_STATUS)
				.body(fairService.createRepository(apiKey, dlsKey, repositories)) ;
	}

	@Tag(description="Create and get datapoint catalog", name="Catalog (Datapoint)")
	@Operation( summary="Create a new catalog inside existing repository in FAIR",
			description = "Assign permissions to users for the directory. Following table describes how permission can be set :" +
					"<table><thead>" +
					"<tr><th><code>Permission</code></th><th><code>Description</code></th></tr></thead>" +
					"<tbody>" +
					"<tr><td><code>R</code></td><td>User can view the catalog metadata and dataset links</td></tr>" +
					"<tr><td><code>W</code></td><td>User can create dataset in this catalog and change catalog metadata</td></tr>" +
					"<tr><td><code>D</code></td><td>User can delete this catalog</td></tr>" +
					"</tbody></table>" )
	@PostMapping(value= URI_BASE + "/repository/{repo-id}/catalog")
	public ResponseEntity <List<DlsResponse>> createCatalog(
			@Parameter(hidden = true)
			@RequestHeader(value="x-api-key", required=false)  String apiKey,
			@Parameter(hidden = true)
			@RequestHeader(value="x-dls-key", required=false)  String dlsKey,
			@PathVariable("repo-id") String repoId,
			@RequestBody List <FAIRCatalogDescriptor> catalogs
	) throws DlsPrivacyException, DlsSecurityException, DlsNotFoundException {
		return ResponseEntity.status(HttpStatus.MULTI_STATUS)
				.body(fairService.createCatalog(apiKey, dlsKey, repoId, catalogs)) ;
	}

	@Tag(description="Create and get datapoint dataset", name="Dataset")
	@Operation( summary="Create a new dataset inside an existing catalog in FAIR",
			description = "Assign permissions to users for the directory. Following table describes how permission can be set :" +
					"<table><thead>" +
					"<tr><th><code>Permission</code></th><th><code>Description</code></th></tr></thead>" +
					"<tbody>" +
					"<tr><td><code>R</code></td><td>User can view the dataset metadata and distribution links</td></tr>" +
					"<tr><td><code>W</code></td><td>User can create distribution in this dataset and change dataset metadata</td></tr>" +
					"<tr><td><code>D</code></td><td>User can delete this dataset</td></tr>" +
					"</tbody></table>" )
	@PostMapping(value= URI_BASE + "/repository/{repo-id}/catalog/{catalog-id}/dataset")
	public ResponseEntity <List<DlsResponse>> createDataset(
			@Parameter(hidden = true)
			@RequestHeader(value="x-api-key", required=false)  String apiKey,
			@Parameter(hidden = true)
			@RequestHeader(value="x-dls-key", required=false)  String dlsKey,
			@PathVariable("repo-id") String repoId,
			@PathVariable("catalog-id") String catalogId,
			@RequestBody List<FAIRDatasetDescriptor> dataset
	) throws DlsPrivacyException, DlsSecurityException, DlsNotFoundException {
		return ResponseEntity.status(HttpStatus.MULTI_STATUS)
				.body(fairService.createDataset(apiKey, dlsKey, repoId, catalogId, dataset)) ;
	}

	@Tag(description="Create and get datapoint distribution", name="Distribution")
	@Operation( summary="Create a new distribution inside an existing dataset in FAIR")
	@PostMapping(value= URI_BASE + "/repository/{repo-id}/catalog/{catalog-id}/dataset/{dataset-id}/distribution")
	public ResponseEntity <List<DlsResponse>> createDistribution(
			@Parameter(hidden = true)
			@RequestHeader(value="x-api-key", required=false)  String apiKey,
			@Parameter(hidden = true)
			@RequestHeader(value="x-dls-key", required=false)  String dlsKey,
			@PathVariable("repo-id") String repoId,
			@PathVariable("catalog-id") String catalogId,
			@PathVariable("dataset-id") String datasetId,
			@RequestBody List<@Valid FAIRDistributionDescriptor> distributions
	) throws DlsPrivacyException, DlsSecurityException, DlsNotFoundException {


		return ResponseEntity.status(HttpStatus.MULTI_STATUS)
				.body(fairService.createDistribution(apiKey, dlsKey, repoId, catalogId, datasetId, distributions)) ;
	}

	@Tag(description="Create and get datapoint repository", name="Repository")
	@Operation( summary="Get a repository by its identifier")
	@GetMapping(value= URI_BASE + "/repository/{repo-id}")
	public FAIRRepository getRepository(
			@Parameter(hidden = true)
			@RequestHeader(value="x-api-key", required=false)  String apiKey,
			@Parameter(hidden = true)
			@RequestHeader(value="x-dls-key", required=false)  String dlsKey,
			@PathVariable("repo-id") String repoId
	) throws DlsPrivacyException, DlsSecurityException, DlsNotFoundException {
		return fairService.getRepository(apiKey, dlsKey, repoId);
	}

	@Tag(description="Find datapoint using various search parameters and delete a datapoint", name="Datapoint")
	@Operation( summary="Find a node in FAIR using metadata")
	@GetMapping(value= URI_BASE)
	public Map<String, Object> find(
			@Parameter(hidden = true)
			@RequestHeader(value="x-api-key", required=false)  String apiKey,
			@Parameter(hidden = true)
			@RequestHeader(value="x-dls-key", required=false)  String dlsKey,
			@RequestParam(required = false, value = "repo-id") String repoId,
			@RequestParam(required = false, value = "catalog-id") String catalogId,
			@RequestParam(required = false, value = "dataset-id") String datasetId,
			@Parameter(allowEmptyValue = false, required = false)
			@RequestParam(required = false) List <String> metadata,
			@RequestParam(required = false, value = "exclude") Set<FAIRServiceHelper.FAIRNodes> exclude,
			@RequestParam(required = false, value = "include-directory") Boolean includeDirectory
	) throws DlsPrivacyException, DlsSecurityException, DlsNotFoundException {

		if(Optional.ofNullable(includeDirectory).orElse(Boolean.FALSE) &&
				(null != repoId || null != catalogId || null != datasetId || null != metadata)) {
			throw new DlsValidationException("If directory is to included in result, filtering based on repo, catalog, dataset or metadata is not possible");
		}
		
		if(metadata!=null && metadata.isEmpty()) {
			throw new DlsValidationException("Invalid Metadata");
		}

		return fairService.find(apiKey, dlsKey, repoId, catalogId, datasetId, metadata, null == exclude ? Sets.newHashSet() : exclude, includeDirectory);
	}

	@Tag(description="Create and get datapoint catalog", name="Catalog (Datapoint)")
	@Operation( summary="Get a catalog by its identifier")
	@GetMapping(value= URI_BASE + "/repository/{repo-id}/catalog/{catalog-id}")
	public FAIRCatalog getCatalog(
			@Parameter(hidden = true)
			@RequestHeader(value="x-api-key", required=false)  String apiKey,
			@Parameter(hidden = true)
			@RequestHeader(value="x-dls-key", required=false)  String dlsKey,
			@PathVariable("repo-id") String repoId,
			@PathVariable("catalog-id") String catalogId
	) throws DlsPrivacyException, DlsSecurityException, DlsNotFoundException {
		return fairService.getCatalog(apiKey, dlsKey, repoId, catalogId) ;
	}

	@Tag(description="Create and get datapoint dataset", name="Dataset")
	@Operation( summary="Get a dataset by its identifier")
	@GetMapping(value= URI_BASE + "/repository/{repo-id}/catalog/{catalog-id}/dataset/{dataset-id}")
	public FAIRDataset getDataset(
			@Parameter(hidden = true)
			@RequestHeader(value="x-api-key", required=false)  String apiKey,
			@Parameter(hidden = true)
			@RequestHeader(value="x-dls-key", required=false)  String dlsKey,
			@PathVariable("repo-id") String repoId,
			@PathVariable("catalog-id") String catalogId,
			@PathVariable("dataset-id") String datasetId
	) throws DlsPrivacyException, DlsSecurityException, DlsNotFoundException {
		return fairService.getDataset(apiKey, dlsKey, repoId, catalogId, datasetId) ;
	}

	@Tag(description="Create and get datapoint distribution", name="Distribution")
	@Operation( summary="Get a distribution by its identifier")
	@GetMapping(value= URI_BASE + "/repository/{repo-id}/catalog/{catalog-id}/dataset/{dataset-id}/distribution/{distribution-id}")
	public FAIRDistribution getDistribution(
			@Parameter(hidden = true)
			@RequestHeader(value="x-api-key", required=false)  String apiKey,
			@Parameter(hidden = true)
			@RequestHeader(value="x-dls-key", required=false)  String dlsKey,
			@PathVariable("repo-id") String repoId,
			@PathVariable("catalog-id") String catalogId,
			@PathVariable("dataset-id") String datasetId,
			@PathVariable("distribution-id") String distributionId
	) throws DlsPrivacyException, DlsSecurityException, DlsNotFoundException {
		return fairService.getDistribution(apiKey, dlsKey, repoId, catalogId, datasetId, distributionId) ;
	}


	@Tag(description="Retrieve, update or delete datapoint permission (only datapoint creator is allowed)", name="Permission")
	@Operation( summary="Update permission of a repository or specific node in it")
	@PutMapping(value= URI_BASE + "/permission/repository/{repo-id}")
	public ResponseEntity <List<DlsResponse>> updatePermission(
			@Parameter(hidden = true)
			@RequestHeader(value="x-api-key", required=false)  String apiKey,
			@Parameter(hidden = true)
			@RequestHeader(value="x-dls-key", required=false)  String dlsKey,
			@PathVariable(value = "repo-id") String repoId,
			@RequestParam(required = false, value = "catalog-id") String catalogId,
			@RequestParam(required = false, value = "dataset-id") String datasetId,
			@RequestParam(required = false, value = "distribution-id") String distId,
			@RequestBody List<@Valid FAIRPermission> permissions
	) throws DlsPrivacyException, DlsSecurityException, DlsNotFoundException {
		return ResponseEntity.status(HttpStatus.MULTI_STATUS)
				.body(fairService.updatePermission(apiKey, dlsKey, repoId, catalogId, datasetId, distId, permissions)) ;
	}

	@Tag(description="Retrieve, update or delete datapoint permission (only datapoint creator is allowed)", name="Permission")
	@Operation( summary="Remove permission from a repository or any specific node in it")
	@DeleteMapping(value= URI_BASE + "/permission/repository/{repo-id}")
	public ResponseEntity <List<DlsResponse>> deletePermission(
			@Parameter(hidden = true)
			@RequestHeader(value="x-api-key", required=false)  String apiKey,
			@Parameter(hidden = true)
			@RequestHeader(value="x-dls-key", required=false)  String dlsKey,
			@PathVariable(value = "repo-id") String repoId,
			@RequestParam(required = false, value = "catalog-id") String catalogId,
			@RequestParam(required = false, value = "dataset-id") String datasetId,
			@RequestParam(required = false, value = "distribution-id") String distId,
			@RequestParam(required = true, value = "user-id") String dlsuser
	) throws DlsPrivacyException, DlsSecurityException, DlsNotFoundException {

		fairService.deletePermission(apiKey, dlsKey, repoId, catalogId, datasetId, distId, dlsuser);
		return ResponseEntity.status(HttpStatus.NO_CONTENT).build() ;
	}

	@Tag(description="Retrieve, update or delete datapoint permission (only datapoint creator is allowed)", name="Permission")
	@Operation( summary="Get permission detail of repository or any specific datapoint in it")
	@GetMapping(value= URI_BASE + "/permission/repository/{repo-id}")
	public ResponseEntity <List<FAIRPermission>> getPermission(
			@Parameter(hidden = true)
			@RequestHeader(value="x-api-key", required=false)  String apiKey,
			@Parameter(hidden = true)
			@RequestHeader(value="x-dls-key", required=false)  String dlsKey,
			@PathVariable(value = "repo-id") String repoId,
			@RequestParam(required = false, value = "catalog-id") String catalogId,
			@RequestParam(required = false, value = "dataset-id") String datasetId,
			@RequestParam(required = false, value = "distribution-id") String distId
	) throws DlsPrivacyException, DlsSecurityException, DlsNotFoundException {

		return ResponseEntity.status(HttpStatus.OK)
				.body(fairService.getPermission(apiKey, dlsKey, repoId, catalogId, datasetId, distId)) ;
	}


//	@Operation( summary="Add new metadata to a repository or any specific node in it")
//	@PostMapping(value= URI_BASE + "/metadata/repository/{repo-id}")
//	public ResponseEntity <List<DlsResponse>> addMetadata(
//			@Parameter(hidden = true)
//			@RequestHeader(value="x-api-key", required=false)  String apiKey,
//			@Parameter(hidden = true)
//			@RequestHeader(value="x-dls-key", required=false)  String dlsKey,
//			@PathVariable(value = "repo-id") String repoId,
//			@RequestParam(required = false, value = "catalog-id") String catalogId,
//			@RequestParam(required = false, value = "dataset-id") String datasetId,
//			@RequestParam(required = false, value = "distribution-id") String distId,
//			@RequestBody Map<String, String> metadata
//	) throws DlsPrivacyException, DlsSecurityException, DlsNotFoundException {
//		return ResponseEntity.status(HttpStatus.MULTI_STATUS)
//				.body(fairService.addMetadata(apiKey, dlsKey, repoId, catalogId, datasetId, distId, metadata)) ;
//	}

	@Tag(description="Update and delete datapoint metadata", name="Metadata (Datapoint)")
	@Operation( summary="Update value of existing metadata of a repository or any specific node in it")
	@PutMapping(value= URI_BASE + "/metadata/repository/{repo-id}")
	public ResponseEntity <List<DlsResponse>> updateMetadata(
			@Parameter(hidden = true)
			@RequestHeader(value="x-api-key", required=false)  String apiKey,
			@Parameter(hidden = true)
			@RequestHeader(value="x-dls-key", required=false)  String dlsKey,
			@PathVariable(value = "repo-id") String repoId,
			@RequestParam(required = false, value = "catalog-id") String catalogId,
			@RequestParam(required = false, value = "dataset-id") String datasetId,
			@RequestParam(required = false, value = "distribution-id") String distId,
			@RequestBody Map<String, String> metadata
	) throws DlsPrivacyException, DlsSecurityException, DlsNotFoundException {
		return ResponseEntity.status(HttpStatus.MULTI_STATUS)
				.body(fairService.updateMetadata(apiKey, dlsKey, repoId, catalogId, datasetId, distId, metadata)) ;
	}

	@Tag(description="Update and delete datapoint metadata", name="Metadata (Datapoint)")
	@Operation( summary="Delete metadata")
	@DeleteMapping(value= URI_BASE + "/metadata/repository/{repo-id}")
	public ResponseEntity <String> deleteMetadata(
			@Parameter(hidden = true)
			@RequestHeader(value="x-api-key", required=false)  String apiKey,
			@Parameter(hidden = true)
			@RequestHeader(value="x-dls-key", required=false)  String dlsKey,
			@PathVariable(value = "repo-id") String repoId,
			@RequestParam(required = false, value = "catalog-id") String catalogId,
			@RequestParam(required = false, value = "dataset-id") String datasetId,
			@RequestParam(required = false, value = "distribution-id") String distId,
			@RequestParam(required = true, value = "key") String key
	) throws DlsPrivacyException, DlsSecurityException, DlsNotFoundException {
		return ResponseEntity.status(HttpStatus.NO_CONTENT)
				.body(fairService.deleteMetadata(apiKey, dlsKey, repoId, catalogId, datasetId, distId, key)) ;
	}

	@Tag(description="Find datapoint using various search parameters and delete a datapoint", name="Datapoint")
	@Operation( summary="Delete a repository or any specific node in it")
	@DeleteMapping(value= URI_BASE + "/repository/{repo-id}")
	public ResponseEntity <String> delete(
			@Parameter(hidden = true)
			@RequestHeader(value="x-api-key", required=false)  String apiKey,
			@Parameter(hidden = true)
			@RequestHeader(value="x-dls-key", required=false)  String dlsKey,
			@PathVariable(value = "repo-id") String repoId,
			@RequestParam(required = false, value = "catalog-id") String catalogId,
			@RequestParam(required = false, value = "dataset-id") String datasetId,
			@RequestParam(required = false, value = "distribution-id") String distId
	) throws DlsPrivacyException, DlsSecurityException, DlsNotFoundException {

		fairService.delete(apiKey, dlsKey, repoId, catalogId, datasetId, distId);
		return ResponseEntity.status(HttpStatus.NO_CONTENT).build() ;
	}

	@Tag(description="Retrieve or create datapoint provenance", name="Provenance")
	@Operation( summary="Attach provenance records")
	@PutMapping(value= URI_BASE + "/provenance/repository/{repo-id}")
	public ResponseEntity <DlsResponse> updateProvenance(
			@Parameter(hidden = true)
			@RequestHeader(value="x-api-key", required=false)  String apiKey,
			@Parameter(hidden = true)
			@RequestHeader(value="x-dls-key", required=false)  String dlsKey,
			@PathVariable(value = "repo-id") String repoId,
			@RequestParam(required = false, value = "catalog-id") String catalogId,
			@RequestParam(required = false, value = "dataset-id") String datasetId,
			@RequestParam(required = false, value = "distribution-id") String distId,

			@RequestBody FAIRProvenanceDescriptor provenance
	) throws DlsPrivacyException, DlsSecurityException, DlsNotFoundException {
		fairService.updateProvenance(apiKey, dlsKey, repoId, catalogId, datasetId, distId, provenance);
		return ResponseEntity.status(HttpStatus.RESET_CONTENT).build() ;
	}

	@Tag(description="Retrieve or create datapoint provenance", name="Provenance")
	@Operation( summary="Fetch provenance records")
	@GetMapping(value= URI_BASE + "/provenance/repository/{repo-id}")
	public ResponseEntity <List<FAIRProvenance>> getProvenance(
			@Parameter(hidden = true)
			@RequestHeader(value="x-api-key", required=false)  String apiKey,
			@Parameter(hidden = true)
			@RequestHeader(value="x-dls-key", required=false)  String dlsKey,
			@PathVariable(value = "repo-id") String repoId,
			@RequestParam(required = false, value = "catalog-id") String catalogId,
			@RequestParam(required = false, value = "dataset-id") String datasetId,
			@RequestParam(required = false, value = "distribution-id") String distId,/*,
			@RequestParam(required = false, value = "event") AuditEvent event,
			@RequestParam(required = false, value = "from-time") Timestamp fromTime,
			@RequestParam(required = false, value = "to-time") Timestamp toTime,*/
			@RequestParam(required = false, value = "page-no", defaultValue = "0") Integer pageNo
	) throws DlsPrivacyException, DlsSecurityException, DlsNotFoundException {
		return ResponseEntity.status(HttpStatus.OK)
				.body(fairService.getProvenance(apiKey, dlsKey, repoId, catalogId, datasetId, distId/*, fromTime, toTime, event*/, pageNo)) ;
	}


}
