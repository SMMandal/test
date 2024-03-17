/*
** This SQL script is the BASE script for Data Lake Service schema.
** It should create a TCUP 9 compatible schema.
** 
** IMPORTANT : Create and connect to a database called 'dls' and an user called 'dlsusr'
** before running this script.
**
** It is strongly recommended to change the value of admin api_key from
** default value 'admin' to something else, before running this script.
** See 'INSERT INTO ...' statement at the end of this file.
*/

CREATE SEQUENCE IF NOT EXISTS hibernate_sequence;

CREATE TABLE IF NOT EXISTS tenant
(
  id bigint NOT NULL,
  api_key character varying(255) NOT NULL,
  tcup_user character varying(255) NOT NULL,
  admin boolean DEFAULT false,
  CONSTRAINT tenant_pkey PRIMARY KEY (id),  
  CONSTRAINT uniqapikey UNIQUE (api_key),
  CONSTRAINT uniqtcupuser UNIQUE (tcup_user)
);

CREATE TABLE IF NOT EXISTS users
(
  id bigint NOT NULL,
  tenant_id bigint NOT NULL,
  dls_key character varying(255) NOT NULL,
  dls_user character varying(255) NOT NULL,
  admin boolean DEFAULT false,
  CONSTRAINT user_pkey PRIMARY KEY (id),
  CONSTRAINT uniqkey UNIQUE (tenant_id, dls_key),
  CONSTRAINT uniqdlsuser UNIQUE (tenant_id, dls_user),
  CONSTRAINT fk_tenant FOREIGN KEY (tenant_id) REFERENCES tenant (id) MATCH SIMPLE ON UPDATE NO ACTION ON DELETE NO ACTION
);


CREATE TABLE IF NOT EXISTS file
(
  id bigint NOT NULL,
  created_on timestamp without time zone,
  deleted boolean,
  external boolean,
  file_name character varying(255),
  fs_path character varying(255),
  savepoint character varying(255),
  size_in_byte bigint,
  uploaded boolean,
  user_id bigint,
  CONSTRAINT file_pkey PRIMARY KEY (id),
  CONSTRAINT fk_user FOREIGN KEY (user_id) REFERENCES users (id) MATCH SIMPLE  ON UPDATE NO ACTION ON DELETE NO ACTION,
  CONSTRAINT uk_file UNIQUE (fs_path)
);


CREATE TABLE IF NOT EXISTS file_meta
(
  id bigint NOT NULL,
  name character varying(255),
  value character varying(255),
  file_id bigint,
  user_id bigint,
  CONSTRAINT file_meta_pkey PRIMARY KEY (id),
  CONSTRAINT fk_file FOREIGN KEY (file_id) REFERENCES file (id) MATCH SIMPLE ON UPDATE NO ACTION ON DELETE NO ACTION,
  CONSTRAINT fk_user FOREIGN KEY (user_id) REFERENCES users (id) MATCH SIMPLE ON UPDATE NO ACTION ON DELETE NO ACTION
);

CREATE TABLE IF NOT EXISTS link
(
  id bigint NOT NULL,  
  lhs_file_id bigint,
  relation character varying(255),
  rhs_file_id bigint,
  user_id bigint,
  CONSTRAINT link_pkey PRIMARY KEY (id),
  CONSTRAINT uk_link UNIQUE (lhs_file_id, relation, rhs_file_id),
  CONSTRAINT fk_file_rhs FOREIGN KEY (rhs_file_id) REFERENCES file (id) MATCH SIMPLE ON UPDATE NO ACTION ON DELETE NO ACTION,
  CONSTRAINT fk_user FOREIGN KEY (user_id) REFERENCES users (id) MATCH SIMPLE ON UPDATE NO ACTION ON DELETE NO ACTION,
  CONSTRAINT fk_file_lhs FOREIGN KEY (lhs_file_id) REFERENCES file (id) MATCH SIMPLE ON UPDATE NO ACTION ON DELETE NO ACTION
);

CREATE TABLE IF NOT EXISTS file_share
(
  id bigint NOT NULL,
  file_id bigint,
  user_id bigint,
  CONSTRAINT file_access_pkey PRIMARY KEY (id),
  CONSTRAINT fk_user FOREIGN KEY (user_id) REFERENCES users (id) MATCH SIMPLE ON UPDATE NO ACTION ON DELETE NO ACTION,
  CONSTRAINT fk_file FOREIGN KEY (file_id) REFERENCES file (id) MATCH SIMPLE  ON UPDATE NO ACTION ON DELETE NO ACTION,
  CONSTRAINT uk_access UNIQUE (file_id, user_id)
);



CREATE OR REPLACE VIEW text_search_result AS 
SELECT 	user_id, 
	file_id, 
	file_name, 
	fs_path, 
	created_on,
	external,
	size_in_byte,
	uploaded,
	deleted,	
	metadata, 
	document, 
	links_to, 
	links_from, 
	string_agg(dls_user,',') AS shared_to 
	
   FROM ( SELECT 
	    F3.user_id,
	    F3.file_id,
	    F3.file_name,
	    F3.fs_path,
	    F3.created_on,
	    F3.external,
	    F3.size_in_byte,
	    F3.uploaded,
	    F3.deleted,	
	    F3.metadata,
	    F3.document,
	    F3.links_to,
	    string_agg((link.relation::text || '='::text) || file.fs_path::text, ','::text) AS links_from,
	    file_share.user_id As share_id
	    
	FROM ( SELECT 
		    F2.user_id,
		    F2.id AS file_id,
		    F2.file_name,
		    F2.fs_path,
		    F2.created_on,
		    F2.external,
		    F2.size_in_byte,
		    F2.uploaded,
		    F2.deleted,	
		    F2.metadata,
		    F2.document,
		    string_agg((link_1.relation::text || '='::text) || F0.fs_path::text, ','::text) AS links_to
		    
		FROM ( SELECT 
			    F1.user_id,
			    F1.id,
			    F1.file_name,
			    F1.fs_path,
			    F1.created_on,
			    F1.external,
			    F1.size_in_byte,
			    F1.uploaded,
			    F1.deleted,	
			    		    
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
				F1.fs_path,  
				F1.created_on,
				F1.external,
				F1.size_in_byte,
				F1.uploaded,
				F1.deleted ) F2
			  
		  LEFT JOIN link link_1 ON F2.id = link_1.lhs_file_id
		  LEFT JOIN file F0 ON F0.id = link_1.rhs_file_id
		  GROUP BY F2.user_id, 
			F2.id, 
			F2.file_name, 
			F2.fs_path, 
			F2.created_on,
			F2.external,
			F2.size_in_byte,
			F2.uploaded,
			F2.deleted, 
			F2.metadata, 
			F2.document) F3
		  
	     LEFT JOIN link ON F3.file_id = link.rhs_file_id
	     LEFT JOIN file ON file.id = link.lhs_file_id
	     LEFT JOIN file_share ON F3.file_id = file_share.file_id
	     
	  GROUP BY F3.user_id, 
		F3.file_id, 
		F3.file_name, 
		F3.fs_path, 
		F3.created_on,
		F3.external,
		F3.size_in_byte,
		F3.uploaded,
		F3.deleted,	
		F3.metadata, 
		F3.document, 
		F3.links_to, 
		share_id ) F

  LEFT JOIN users on users.id = share_id
  GROUP BY user_id, 
	file_id, 
	file_name, 
	fs_path, 
	created_on,
	external,
	size_in_byte,
	uploaded,
	deleted, 
	metadata, 
	document, 
	links_to, 
	links_from;



CREATE OR REPLACE VIEW text_search_result_shared AS 
 SELECT text_search_result.user_id,
    text_search_result.file_id,
    text_search_result.file_name,
    text_search_result.fs_path,
    text_search_result.created_on,
    text_search_result.external,
    text_search_result.size_in_byte,
    text_search_result.uploaded,
    text_search_result.deleted,
    text_search_result.metadata,
    text_search_result.document,
    text_search_result.links_to,
    text_search_result.links_from,
    text_search_result.shared_to
   FROM text_search_result
UNION
 SELECT f.user_id,
    t.file_id,
    t.file_name,
    t.fs_path,
    t.created_on,
    t.external,
    t.size_in_byte,
    t.uploaded,
    t.deleted,
    t.metadata,
    t.document,
    t.links_to,
    t.links_from,
    NULL::text AS shared_to
   FROM text_search_result t
     JOIN file_share f ON t.file_id = f.file_id;


INSERT INTO tenant (id, api_key, tcup_user, admin) VALUES (0, 'admin', 'tcupadmin', true) ON CONFLICT DO NOTHING;
	
