drop table file_links ;
drop table catalog_text_search ;

CREATE OR REPLACE VIEW file_links AS SELECT
    link.lhs_file_id AS file_id,
    string_agg((link.relation::text || '='::text) || F.fs_path::text, ','::text) AS links_to
FROM link
	LEFT JOIN file ON file.id = link.lhs_file_id
	LEFT JOIN file F ON F.id = link.rhs_file_id
GROUP BY file_id;




CREATE OR REPLACE VIEW catalog_text_search AS
SELECT file.id,
	file.bundle,
	file.bundled,
	file.created_on,
	file.deleted,
	file.deleted_on,
	file.external,
	file.file_name,
	file.fs_path,
	file.lock,
	file.qualifier,
	file.savepoint,
	file.shared_to,
	file.size_in_byte,
	file.uploaded,
	file.upload_status,
	file.user_id,
	file.directory_id,
	((setweight(to_tsvector(file.file_name::text), 'A'::"char") ||
		to_tsvector(COALESCE(string_agg(file_meta.name::text, ' '::text), ''::text))) ||
		to_tsvector(COALESCE(string_agg(file_meta.value::text, ' '::text), ''::text))) ||
		to_tsvector(replace(file.fs_path::text, '/'::text, ' '::text)) AS document
FROM file
	LEFT JOIN file_meta ON file.id = file_meta.file_id
GROUP BY file.id,
    file.bundled,
	file.created_on,
	file.deleted,
	file.deleted_on,
	file.external,
	file.file_name,
	file.fs_path,
	file.lock,
	file.qualifier,
	file.savepoint,
	file.shared_to,
	file.size_in_byte,
	file.uploaded,
	file.upload_status,
	file.directory_id,
	file.user_id;

alter view file_links owner to dlsusr;
alter view catalog_text_search owner to dlsusr;

CREATE INDEX idx_file_fsPath ON file (fs_path);
CREATE INDEX idx_meta_file_id ON file_meta (file_id);
CREATE INDEX idx_meta_name ON file_meta (name);
CREATE INDEX idx_meta_value ON file_meta (value);
CREATE INDEX idx_link_lhs_file_id ON link (lhs_file_id);
CREATE INDEX idx_link_rhs_file_id ON link (rhs_file_id);