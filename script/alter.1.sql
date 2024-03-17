/*
** This SQL script is the ALTER script for Data Lake Service schema.
** It should create a TCUP 10 compatible schema.
** 
** IMPORTANT : Create and connect to a database called 'dls' and an user called 'dlsusr'
** before running this script.
**
** Run BASE script called schema.sql first for new installation.
** Alternatively, run this script directly on TCUP 9 database to upgrade to TCUP 10 database.
** To rollback to TCUP 9 database, run ROLLBACK script called revert.1.sql
*/

alter table file add column if not exists deleted_on timestamp without time zone;

drop view statistics ;
drop view text_search_result_shared;
drop view text_search_result;

CREATE OR REPLACE VIEW text_search_result AS 
SELECT 	user_id, 
	file_id, 
	file_name, 
	savepoint,
	fs_path, 
	created_on,
	external,
	size_in_byte,
	uploaded,
	deleted,
	deleted_on,	
	metadata, 
	document, 
	links_to, 
	links_from, 
	string_agg(dls_user,',') AS shared_to 
	
   FROM ( SELECT 
	    F3.user_id,
	    F3.file_id,
	    F3.file_name,
	    F3.savepoint,
	    F3.fs_path,
	    F3.created_on,
	    F3.external,
	    F3.size_in_byte,
	    F3.uploaded,
	    F3.deleted,
	    F3.deleted_on,	
	    F3.metadata,
	    F3.document,
	    F3.links_to,
	    string_agg((link.relation::text || '='::text) || file.fs_path::text, ','::text) AS links_from,
	    file_share.user_id As share_id
	    
	FROM ( SELECT 
		    F2.user_id,
		    F2.id AS file_id,
		    F2.file_name,
		    F2.savepoint,
		    F2.fs_path,
		    F2.created_on,
		    F2.external,
		    F2.size_in_byte,
		    F2.uploaded,
		    F2.deleted,	
		    F2.deleted_on,
		    F2.metadata,
		    F2.document,
		    string_agg((link_1.relation::text || '='::text) || F0.fs_path::text, ','::text) AS links_to
		    
		FROM ( SELECT 
			    F1.user_id,
			    F1.id,
			    F1.file_name,
			    F1.savepoint,
			    F1.fs_path,
			    F1.created_on,
			    F1.external,
			    F1.size_in_byte,
			    F1.uploaded,
			    F1.deleted,	
			    F1.deleted_on,   
			    string_agg((file_meta.name::text || '='::text) || file_meta.value::text, ','::text) 
				AS metadata,
							    
			    ((setweight(to_tsvector(F1.file_name::text), 'A'::"char") || 
					to_tsvector(COALESCE(string_agg(file_meta.name::text, ' '::text), ''::text))) || 
					to_tsvector(COALESCE(string_agg(file_meta.value::text, ' '::text), ''::text))) || 
					to_tsvector(replace(F1.fs_path::text, '/'::text, ' '::text)) 
				AS document

			FROM file F1
			LEFT JOIN file_meta ON F1.id = file_meta.file_id
			     
			GROUP BY  F1.user_id, 
				F1.id, 
				F1.file_name, 
				F1.savepoint,
				F1.fs_path,  
				F1.created_on,
				F1.external,
				F1.size_in_byte,
				F1.uploaded,
				F1.deleted,
				F1.deleted_on ) F2
			  
		  LEFT JOIN link link_1 ON F2.id = link_1.lhs_file_id
		  LEFT JOIN file F0 ON F0.id = link_1.rhs_file_id
		  GROUP BY F2.user_id, 
			F2.id, 
			F2.file_name, 
			F2.savepoint,
			F2.fs_path, 
			F2.created_on,
			F2.external,
			F2.size_in_byte,
			F2.uploaded,
			F2.deleted, 
			F2.deleted_on,
			F2.metadata, 
			F2.document) F3
		  
	     LEFT JOIN link ON F3.file_id = link.rhs_file_id
	     LEFT JOIN file ON file.id = link.lhs_file_id
	     LEFT JOIN file_share ON F3.file_id = file_share.file_id
	     
	  GROUP BY F3.user_id, 
		F3.file_id, 
		F3.file_name, 
		F3.savepoint,
		F3.fs_path, 
		F3.created_on,
		F3.external,
		F3.size_in_byte,
		F3.uploaded,
		F3.deleted,	
		F3.deleted_on,
		F3.metadata, 
		F3.document, 
		F3.links_to, 
		share_id ) F

  LEFT JOIN users on users.id = share_id
  GROUP BY user_id, 
	file_id, 
	file_name, 
	savepoint,
	fs_path, 
	created_on,
	external,
	size_in_byte,
	uploaded,
	deleted, 
	deleted_on, 
	metadata, 
	document, 
	links_to, 
	links_from;



CREATE OR REPLACE VIEW text_search_result_shared AS 
 SELECT text_search_result.user_id,
    text_search_result.file_id,
    text_search_result.file_name,
    text_search_result.savepoint,
    text_search_result.fs_path,
    text_search_result.created_on,
    text_search_result.external,
    text_search_result.size_in_byte,
    text_search_result.uploaded,
    text_search_result.deleted,
    text_search_result.deleted_on,
    text_search_result.metadata,
    text_search_result.document,
    text_search_result.links_to,
    text_search_result.links_from,
    text_search_result.shared_to,
    TRUE::boolean own_file
   FROM text_search_result
UNION
 SELECT f.user_id,
    t.file_id,
    t.file_name,
    t.savepoint,
    t.fs_path,
    t.created_on,
    t.external,
    t.size_in_byte,
    t.uploaded,
    t.deleted,
    t.deleted_on,
    t.metadata,
    t.document,
    t.links_to,
    t.links_from,
    NULL::text AS shared_to,
    FALSE::boolean own_file
   FROM text_search_result t
     JOIN file_share f ON t.file_id = f.file_id;
     
     
alter table file alter deleted set default false;
alter table file alter external set default false;
alter table file drop constraint if exists uk_file;
CREATE UNIQUE INDEX if not exists uk_file_deleted ON file (fs_path, deleted_on) WHERE deleted_on IS NOT NULL;
CREATE UNIQUE INDEX if not exists uk_file ON file (fs_path) WHERE deleted_on IS NULL;

CREATE OR REPLACE VIEW statistics AS 
	select user_id, sum(size_in_byte) totalVolume, count(*) totalCount, 
	max(created_on) lastUploaded, min(created_on) firstUploaded, 
	(select count(*) from text_search_result_shared where deleted = true ) totalDeleted, 
	(select count(*) from text_search_result_shared where external = true) totalExternal, 
	(select count(*) from text_search_result_shared where uploaded = false) totalFailed, 
	(select count(*) from text_search_result_shared where own_file = false) totalShared 
	from text_search_result_shared group by user_id ;

update file set deleted = false where deleted is null;
update file set external = false where external is null;
	
