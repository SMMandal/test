DROP VIEW metadata_view;

CREATE OR REPLACE VIEW metadata_view AS
SELECT file_meta.id, 'F' AS type, file_id type_id, ltrim(name,'1234567890@') AS name, value, dls_user created_by, schema_id,
 CASE
     WHEN 'private' = ANY(qualifier) THEN user_id
     ELSE NULL
 END AS private_to
 FROM file_meta JOIN users on user_id = users.id WHERE name NOT LIKE 'dls:%'
UNION
SELECT directory_meta.id, 'D' AS type, directory_id, name, value, dls_user, schema_id, NULL FROM directory_meta
JOIN users on user_id = users.id
WHERE is_meta = true;

ALTER VIEW metadata_view OWNER TO "dlsusr";

DROP VIEW explorer_view;
CREATE OR REPLACE VIEW explorer_view AS

SELECT 'F' AS type, file.id, file_name AS name, fs_path AS path,
	directory AS parent, size_in_byte AS size, file.created_on, 
	users.dls_user AS created_by,  savepoint, uploaded, upload_status,
	lock, bundled, file.qualifier, shared_to, 
	coalesce(permitted_users, ARRAY[file.user_id]) AS permitted_users, 
	array_remove(ARRAY_AGG(fm.id), NULL) AS metadata_ids, 
	JSONB_AGG(json_strip_nulls(json_build_object(
		'name',ltrim(name,'1234567890@'),
		'value',value,
		'createdBy',fm.user_id,
		'privateTo', 
		CASE
	     		WHEN 'private' = ANY(fm.qualifier) 
	     			THEN fm.user_id
	     		ELSE NULL
	     	END 
     	))) AS metadata_json,
     	users.tenant_id AS tenant_id
FROM file
	LEFT JOIN directory ON file.directory_id = directory.id	
	LEFT JOIN (SELECT directory_id, ARRAY_AGG(permitted_user) AS permitted_users 
		FROM permission 
		WHERE action LIKE '%R%' 
		GROUP BY directory_id) P 
	ON P.directory_id = file.directory_id
	LEFT JOIN (select * from file_meta where name not like 'dls:%') fm ON file.id = fm.file_id
	LEFT JOIN users ON users.id = file.user_id
WHERE file.deleted <> true AND fs_path LIKE '%'||file_name
GROUP BY file.id, directory.directory, p.permitted_users, users.tenant_id, users.dls_user
	
	
UNION	
	
	
SELECT 'D' AS type, d1.id,
	regexp_replace(d1.directory,'.*/','') AS name, d1.directory, 
	rtrim((regexp_match(d1.directory, '/.*/'))[1], '/'), 
	NULL AS size_in_byte, d1.created_on, users.dls_user,
	NULL AS savepoint, true AS uploaded, NULL AS upload_status, 
	NULL AS lock, NULL AS bundled, NULL AS qualifier, 
	NULL AS shared_to, permitted_users, 
	array_remove(ARRAY_AGG(dm.id), NULL), 
	JSONB_AGG(json_strip_nulls(json_build_object(
		'name',name,
		'value',value,
		'createdBy',dm.user_id))),
	d1.tenant_id
FROM directory d1
	LEFT JOIN (SELECT directory_id, ARRAY_AGG(permitted_user) AS permitted_users 
		FROM permission 
		WHERE action like '%A%' 
		GROUP BY directory_id) P 
	ON P.directory_id = d1.id
	LEFT JOIN (SELECT * FROM directory_meta WHERE is_meta = true) dm ON d1.id = dm.directory_id
	LEFT JOIN users ON users.id = d1.created_by_id
GROUP BY d1.id, p.permitted_users,users.dls_user;
	
ALTER VIEW explorer_view OWNER TO dlsusr;
	