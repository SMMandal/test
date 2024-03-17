ALTER TABLE audit ALTER COLUMN entity TYPE TEXT, 
		  ALTER COLUMN event TYPE TEXT;
		  
ALTER TABLE comment ALTER COLUMN comment TYPE TEXT;		  
		  
drop view statistics ; drop view catalog_text_search ;
		  
ALTER TABLE file ALTER COLUMN file_name TYPE TEXT, 
		 ALTER COLUMN fs_path TYPE TEXT,
		 ALTER COLUMN savepoint TYPE TEXT,
		 ALTER COLUMN bundle_hash TYPE TEXT,
		 ALTER COLUMN qualifier TYPE TEXT ARRAY,
		 ALTER COLUMN shared_to TYPE TEXT ARRAY,
		 ALTER COLUMN upload_status TYPE TEXT;
		 
ALTER TABLE file_meta ALTER COLUMN name TYPE TEXT, 
		           ALTER COLUMN value TYPE TEXT,
		           ALTER COLUMN qualifier TYPE TEXT ARRAY;
		 
ALTER TABLE directory ALTER COLUMN directory TYPE TEXT, 
		      ALTER COLUMN enforcement_type TYPE TEXT;
		  
ALTER TABLE directory_meta ALTER COLUMN name TYPE TEXT, 
		           ALTER COLUMN type TYPE TEXT,
		           ALTER COLUMN value TYPE TEXT;	

ALTER TABLE link ALTER COLUMN relation TYPE TEXT;

ALTER TABLE relation ALTER COLUMN description TYPE TEXT, 
		     ALTER COLUMN relationship_name TYPE TEXT;
		      
ALTER TABLE meta_schema ALTER COLUMN name TYPE TEXT, 
		        ALTER COLUMN type TYPE TEXT,
		        ALTER COLUMN description TYPE TEXT;		
		           
ALTER TABLE users ALTER COLUMN dls_key TYPE TEXT, 
		  ALTER COLUMN dls_user TYPE TEXT;	
		           
ALTER TABLE tenant ALTER COLUMN api_key TYPE TEXT, 
		   ALTER COLUMN tcup_user TYPE TEXT,
		   ALTER COLUMN organization TYPE TEXT;		           		           
		                 		           	  		 

CREATE OR REPLACE VIEW public.catalog_text_search AS
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
    file.storage,
    file.shared_to,
    file.size_in_byte,
    file.uploaded,
    file.upload_status,
    COALESCE(permission.permitted_user, file.user_id) AS user_id,
    permission.acquired_user,
    permission.action,
    file.directory_id,
    (((setweight(to_tsvector((file.file_name)::text), 'A'::"char") || to_tsvector(COALESCE(string_agg((file_meta.name)::text, ' '::text), ''::text))) || to_tsvector(COALESCE(string_agg((file_meta.value)::text, ' '::text), ''::text))) || to_tsvector(replace(replace((file.fs_path)::text, '/'::text, ' '::text), '.'::text, ' .'::text))) AS document
   FROM ((public.file
     LEFT JOIN public.file_meta ON ((file.id = file_meta.file_id)))
     LEFT JOIN public.permission ON (((file.directory_id = permission.directory_id) AND ((permission.action)::text ~~ '%R%'::text))))
  GROUP BY file.id, file.bundled, file.created_on, file.deleted, file.deleted_on, file.external, file.file_name, file.fs_path, file.lock, file.qualifier, file.savepoint, file.shared_to, file.storage, file.size_in_byte, file.uploaded, file.upload_status, file.directory_id, file.user_id, permission.permitted_user, permission.acquired_user, permission.action;
  
  
  ALTER TABLE public.catalog_text_search OWNER TO dlsusr;
  
  
  
CREATE VIEW public.statistics AS
 SELECT tsr.user_id,
    sum(tsr.size_in_byte) AS totalvolume,
    count(*) AS totalcount,
    max(tsr.created_on) AS lastuploaded,
    min(tsr.created_on) AS firstuploaded,
    ( SELECT count(*) AS count
           FROM public.catalog_text_search tsr1
          WHERE ((NOT ('dls:internal'::text IN ( SELECT fm.name
                   FROM public.file_meta fm
                  WHERE (tsr1.id = fm.file_id)))) AND (tsr1.deleted = true) AND (tsr.user_id = tsr1.user_id))) AS totaldeleted,
    ( SELECT count(*) AS count
           FROM public.catalog_text_search tsr2
          WHERE ((tsr2.external = true) AND (tsr.user_id = tsr2.user_id))) AS totalexternal,
    ( SELECT count(*) AS count
           FROM public.catalog_text_search tsr3
          WHERE ((tsr3.uploaded = false) AND (tsr.user_id = tsr3.user_id))) AS totalfailed,
    ( SELECT count(*) AS count
           FROM public.file_share tsr4
          WHERE (tsr.user_id = tsr4.user_id)) AS totalshared,
    ( SELECT count(*) AS count
           FROM public.file tsr5
          WHERE ((tsr5.bundled = true) AND (tsr.user_id = tsr5.user_id))) AS totalbundled
   FROM public.catalog_text_search tsr
  WHERE (NOT ('dls:internal'::text IN ( SELECT fm.name
           FROM public.file_meta fm
          WHERE (tsr.id = fm.file_id))))
  GROUP BY tsr.user_id;


ALTER TABLE public.statistics OWNER TO dlsusr;
		 
