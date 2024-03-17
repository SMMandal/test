ALTER TABLE users DROP CONSTRAINT user_pkey;
ALTER TABLE file_share DROP CONSTRAINT file_access_pkey;
ALTER TABLE tenant DROP CONSTRAINT uniqapikey;
ALTER TABLE users DROP CONSTRAINT uniqkey;
ALTER TABLE users DROP CONSTRAINT fk_tenant;
ALTER TABLE file_meta DROP CONSTRAINT fk_file;
ALTER TABLE file_meta DROP CONSTRAINT fk_user;
ALTER TABLE file_share DROP CONSTRAINT fk_file;
ALTER TABLE file_share DROP CONSTRAINT fk_user;
ALTER TABLE link DROP CONSTRAINT fk_file_lhs;
ALTER TABLE link DROP CONSTRAINT fk_file_rhs;
ALTER TABLE link DROP CONSTRAINT fk_user;
ALTER TABLE file DROP CONSTRAINT fk_user;
ALTER TABLE meta_schema DROP CONSTRAINT uniqmetakey;
DROP INDEX uk_file;
DROP INDEX uk_file_deleted;

ALTER TABLE tenant
	ADD COLUMN allocated_storage bigint,
	ADD COLUMN allow_adhoc boolean,
	ADD COLUMN max_key_len integer,
	ADD COLUMN max_meta_per_file integer,
	ADD COLUMN max_value_len integer,
	ADD COLUMN organization character varying(255) COLLATE pg_catalog."default",
	ADD COLUMN schematic boolean,
	ADD COLUMN used_storage bigint,
	ALTER COLUMN "admin" DROP DEFAULT;

ALTER TABLE users
	ADD COLUMN last_updated_on timestamp without time zone,
	ADD COLUMN org_position text[] COLLATE pg_catalog."default",
	ADD COLUMN last_updated_by_id bigint,
	ALTER COLUMN "admin" DROP DEFAULT,
	ALTER COLUMN tenant_id DROP NOT NULL;

ALTER TABLE file_meta
	ADD COLUMN qualifier character varying(50)[] COLLATE pg_catalog."default",
	ADD COLUMN value_numeric double precision;

ALTER TABLE file
	ADD COLUMN bundle json,
	ADD COLUMN bundle_hash character varying(255) COLLATE pg_catalog."default",
	ADD COLUMN bundled boolean,
	ADD COLUMN lock character(2) COLLATE pg_catalog."default",
	ADD COLUMN qualifier character varying(50)[] COLLATE pg_catalog."default",
	ADD COLUMN shared_to character varying(255)[] COLLATE pg_catalog."default",
	ADD COLUMN storage character varying(2) COLLATE pg_catalog."default",
	ADD COLUMN upload_status character varying(255) COLLATE pg_catalog."default",
	ADD COLUMN directory_id bigint,
	ALTER COLUMN deleted DROP DEFAULT,
	ALTER COLUMN "external" DROP DEFAULT;

ALTER TABLE users ADD CONSTRAINT users_pkey PRIMARY KEY (id);
ALTER TABLE file_share ADD CONSTRAINT file_share_pkey PRIMARY KEY (id);
ALTER TABLE tenant ADD CONSTRAINT uniqkey UNIQUE (api_key);
ALTER TABLE users ADD CONSTRAINT uniqkey_users UNIQUE (tenant_id, dls_key);

ALTER TABLE users
	ADD CONSTRAINT fkl68ecas1t5bsdsdkq9m1yy4eo FOREIGN KEY (tenant_id)
        REFERENCES public.tenant (id) MATCH SIMPLE
        ON UPDATE NO ACTION
        ON DELETE NO ACTION
        NOT VALID;

ALTER TABLE users
	ADD CONSTRAINT fknkbjdibyxp74eocs4ra9kt472 FOREIGN KEY (last_updated_by_id)
        REFERENCES public.users (id) MATCH SIMPLE
        ON UPDATE NO ACTION
        ON DELETE NO ACTION
        NOT VALID;

ALTER TABLE file_meta
	ADD CONSTRAINT fk2luajf8t4fxu5o0sm09hcggro FOREIGN KEY (file_id)
        REFERENCES public.file (id) MATCH SIMPLE
        ON UPDATE NO ACTION
        ON DELETE NO ACTION
        NOT VALID;

ALTER TABLE file_meta
	ADD CONSTRAINT fkhrqruh22ypy2jsmtq5bdmpi03 FOREIGN KEY (user_id)
        REFERENCES public.users (id) MATCH SIMPLE
        ON UPDATE NO ACTION
        ON DELETE NO ACTION
        NOT VALID;

ALTER TABLE file_share
	ADD CONSTRAINT fk20ns9fuwr8up9cip2rvbwysr7 FOREIGN KEY (file_id)
        REFERENCES public.file (id) MATCH SIMPLE
        ON UPDATE NO ACTION
        ON DELETE NO ACTION
        NOT VALID;

ALTER TABLE file_share
	ADD CONSTRAINT fkhbgi9al8su1aox24sxtl7go4y FOREIGN KEY (user_id)
        REFERENCES public.users (id) MATCH SIMPLE
        ON UPDATE NO ACTION
        ON DELETE NO ACTION
        NOT VALID;

ALTER TABLE link
	ADD CONSTRAINT fk6241wyjpvm38ik3x9g0nemg4d FOREIGN KEY (rhs_file_id)
        REFERENCES public.file (id) MATCH SIMPLE
        ON UPDATE NO ACTION
        ON DELETE NO ACTION
        NOT VALID;

ALTER TABLE link
	ADD CONSTRAINT fkkk6r35h0380825muu4xnh7ulr FOREIGN KEY (user_id)
        REFERENCES public.users (id) MATCH SIMPLE
        ON UPDATE NO ACTION
        ON DELETE NO ACTION
        NOT VALID;

ALTER TABLE link
	ADD CONSTRAINT fkpdmdv1vx75se6wm0r1njelw3m FOREIGN KEY (lhs_file_id)
        REFERENCES public.file (id) MATCH SIMPLE
        ON UPDATE NO ACTION
        ON DELETE NO ACTION
        NOT VALID;

alter table file drop constraint if exists uk_file;
CREATE UNIQUE INDEX if not exists uk_file_deleted ON file (fs_path, deleted_on) WHERE deleted_on IS NOT NULL;
CREATE UNIQUE INDEX if not exists uk_file ON file (fs_path) WHERE deleted_on IS NULL;

ALTER TABLE file
	ADD CONSTRAINT fke70ql3orpo0ghvfmqccv27ng FOREIGN KEY (user_id)
        REFERENCES public.users (id) MATCH SIMPLE
        ON UPDATE NO ACTION
        ON DELETE NO ACTION
        NOT VALID;

ALTER TABLE file
	ADD CONSTRAINT fkl6daop96981fsq83ouac4o4pk FOREIGN KEY (directory_id)
        REFERENCES public.directory (id) MATCH SIMPLE
        ON UPDATE NO ACTION
        ON DELETE NO ACTION
        NOT VALID;


-- Table: public.directory

-- DROP TABLE public.directory;

CREATE TABLE public.directory
(
    id bigint NOT NULL,
    created_on timestamp without time zone,
    deleted boolean,
    deleted_on timestamp without time zone,
    directory character varying(255) COLLATE pg_catalog."default",
    enforcement_type character varying(255) COLLATE pg_catalog."default",
    parent bigint,
    created_by_id bigint,
    tenant_id bigint,
    CONSTRAINT directory_pkey PRIMARY KEY (id),
    CONSTRAINT fk2hpb5lcs6q7xdnl8cd9yp1ifb FOREIGN KEY (created_by_id)
        REFERENCES public.users (id) MATCH SIMPLE
        ON UPDATE NO ACTION
        ON DELETE NO ACTION
        NOT VALID,
    CONSTRAINT fk53u9iwnmiot4uyrrt8u5o4l6g FOREIGN KEY (tenant_id)
        REFERENCES public.tenant (id) MATCH SIMPLE
        ON UPDATE NO ACTION
        ON DELETE NO ACTION
        NOT VALID
)

TABLESPACE pg_default;

CREATE UNIQUE INDEX if not exists uk_directory_deleted ON directory (tenant_id, directory, deleted_on) WHERE deleted_on IS NOT NULL;
CREATE UNIQUE INDEX if not exists uk_directory ON directory (tenant_id, directory) WHERE deleted_on IS NULL;

ALTER TABLE public.directory OWNER to dlsusr;


-- Table: public.directory_meta

-- DROP TABLE public.directory_meta;

CREATE TABLE public.directory_meta
(
    id bigint NOT NULL,
    deleted boolean,
    deleted_on timestamp without time zone,
    name character varying(255) COLLATE pg_catalog."default",
    type character varying(255) COLLATE pg_catalog."default",
    value character varying(255) COLLATE pg_catalog."default",
    value_mandatory boolean,
    value_numeric double precision,
    directory_id bigint,
    user_id bigint,
    CONSTRAINT directory_meta_pkey PRIMARY KEY (id)
,
    CONSTRAINT fk6ljmywb77ntbb2mnmnrlsdr0u FOREIGN KEY (directory_id)
        REFERENCES public.directory (id) MATCH SIMPLE
        ON UPDATE NO ACTION
        ON DELETE NO ACTION
        NOT VALID,
    CONSTRAINT fkcpwcluox1vmieufd2kd1bgnuk FOREIGN KEY (user_id)
        REFERENCES public.users (id) MATCH SIMPLE
        ON UPDATE NO ACTION
        ON DELETE NO ACTION
        NOT VALID
)

TABLESPACE pg_default;

ALTER TABLE public.directory_meta OWNER to dlsusr;


-- Table: public.permission

-- DROP TABLE public.permission;

CREATE TABLE public.permission
(
    id bigint NOT NULL,
    acquired_user bigint[],
    action character varying(10) COLLATE pg_catalog."default",
    permitted_user bigint,
    directory_id bigint,
    tenant_id bigint,
    user_id bigint,
    CONSTRAINT permission_pkey PRIMARY KEY (id),
    CONSTRAINT Unique_Permission UNIQUE (permitted_user, directory_id),
    CONSTRAINT fk516d52s8lgik1l7jf819qtbyh FOREIGN KEY (tenant_id)
        REFERENCES public.tenant (id) MATCH SIMPLE
        ON UPDATE NO ACTION
        ON DELETE NO ACTION
        NOT VALID,
    CONSTRAINT fk7jaf1bv7f4a68uru5m2kdjvqs FOREIGN KEY (directory_id)
        REFERENCES public.directory (id) MATCH SIMPLE
        ON UPDATE NO ACTION
        ON DELETE NO ACTION
        NOT VALID,
    CONSTRAINT fktpvwdvckg86mpuk9o2j1h6t15 FOREIGN KEY (user_id)
        REFERENCES public.users (id) MATCH SIMPLE
        ON UPDATE NO ACTION
        ON DELETE NO ACTION
        NOT VALID
)

TABLESPACE pg_default;

ALTER TABLE public.permission OWNER to dlsusr;
-- Table: public.comment

-- DROP TABLE public.comment;

CREATE TABLE public.comment
(
    id bigint NOT NULL,
    comment character varying(255) COLLATE pg_catalog."default",
    created_on timestamp without time zone,
    file_id bigint,
    tenant_id bigint,
    user_id bigint,
    CONSTRAINT comment_pkey PRIMARY KEY (id),
    CONSTRAINT uk_timestamp UNIQUE (created_on, user_id)
,
    CONSTRAINT fk1sfbbndhkqh7yv705na5xq6d6 FOREIGN KEY (tenant_id)
        REFERENCES public.tenant (id) MATCH SIMPLE
        ON UPDATE NO ACTION
        ON DELETE NO ACTION
        NOT VALID,
    CONSTRAINT fknxrvghegv727tcpwcpqodlvpg FOREIGN KEY (file_id)
        REFERENCES public.file (id) MATCH SIMPLE
        ON UPDATE NO ACTION
        ON DELETE NO ACTION
        NOT VALID,
    CONSTRAINT fkqm52p1v3o13hy268he0wcngr5 FOREIGN KEY (user_id)
        REFERENCES public.users (id) MATCH SIMPLE
        ON UPDATE NO ACTION
        ON DELETE NO ACTION
        NOT VALID
)

TABLESPACE pg_default;

ALTER TABLE public.comment
    OWNER to dlsusr;


-- Table: public.relation

-- DROP TABLE public.relation;

CREATE TABLE public.relation
(
    id bigint NOT NULL,
    deleted boolean,
    deleted_on timestamp without time zone,
    description character varying(255) COLLATE pg_catalog."default",
    relationship_name character varying(255) COLLATE pg_catalog."default" NOT NULL,
    CONSTRAINT relation_pkey PRIMARY KEY (id),
    CONSTRAINT uniqrelationkey UNIQUE (relationship_name)

)

TABLESPACE pg_default;

ALTER TABLE public.relation OWNER to dlsusr;




-- Table: public.meta_schema

-- DROP TABLE public.meta_schema;

CREATE TABLE public.meta_schema
(
    id bigint NOT NULL,
    deleted boolean,
    deleted_on timestamp without time zone,
    description character varying(255) COLLATE pg_catalog."default",
    name character varying(255) COLLATE pg_catalog."default" NOT NULL,
    type character varying(255) COLLATE pg_catalog."default" NOT NULL,
    tenant_id bigint NOT NULL,
    CONSTRAINT meta_schema_pkey PRIMARY KEY (id),
    CONSTRAINT fkir6h8exuvr3nuyyg2wca7hdv2 FOREIGN KEY (tenant_id)
        REFERENCES public.tenant (id) MATCH SIMPLE
        ON UPDATE NO ACTION
        ON DELETE NO ACTION
        NOT VALID
)

TABLESPACE pg_default;

CREATE UNIQUE INDEX uk_schema ON public.meta_schema USING btree (tenant_id, lower(name)) WHERE (deleted_on IS NULL);

CREATE UNIQUE INDEX uk_schema_deleted ON public.meta_schema USING btree (tenant_id, lower(name), deleted_on) WHERE (deleted_on IS NOT NULL);

ALTER TABLE public.meta_schema OWNER to dlsusr;

ALTER TABLE public.file_meta ADD COLUMN schema_id bigint ;
ALTER TABLE public.file_meta ADD CONSTRAINT file_schema_fk FOREIGN KEY (schema_id)
REFERENCES public.meta_schema (id) MATCH SIMPLE
  ON UPDATE NO ACTION
  ON DELETE NO ACTION
NOT VALID;
CREATE UNIQUE INDEX uk_directory_meta ON public.directory_meta USING btree (directory_id, lower(name));

ALTER TABLE public.directory_meta ADD COLUMN schema_id bigint ;
ALTER TABLE public.directory_meta ADD CONSTRAINT dir_schema_fk FOREIGN KEY (schema_id)
REFERENCES public.meta_schema (id) MATCH SIMPLE
  ON UPDATE NO ACTION
  ON DELETE NO ACTION
NOT VALID;

drop view text_search_result_shared;
drop view text_search_result;


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
	file.storage,
	file.shared_to,
	file.size_in_byte,
	file.uploaded,
	file.upload_status,
	COALESCE(permission.permitted_user, file.user_id) user_id,
  permission.acquired_user,
  permission.action,
	file.directory_id,
	((setweight(to_tsvector(file.file_name::text), 'A'::"char") ||
		to_tsvector(COALESCE(string_agg(file_meta.name::text, ' '::text), ''::text))) ||
		to_tsvector(COALESCE(string_agg(file_meta.value::text, ' '::text), ''::text))) ||
		to_tsvector(replace(replace(file.fs_path::text, '/'::text, ' '::text), '.'::text, ' .'::text)) AS document
FROM file
	LEFT JOIN file_meta ON file.id = file_meta.file_id
  LEFT JOIN permission ON file.directory_id = permission.directory_id and permission.action like '%R%'
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
  file.storage,
	file.size_in_byte,
	file.uploaded,
	file.upload_status,
	file.directory_id,
	file.user_id,
  permission.permitted_user,
  permission.acquired_user,
  permission.action;

alter view file_links owner to dlsusr;
alter view catalog_text_search owner to dlsusr;

CREATE INDEX idx_file_fsPath ON file (fs_path);
CREATE INDEX idx_meta_file_id ON file_meta (file_id);
CREATE INDEX idx_meta_name ON file_meta (name);
CREATE INDEX idx_meta_value ON file_meta (value);
CREATE INDEX idx_link_lhs_file_id ON link (lhs_file_id);
CREATE INDEX idx_link_rhs_file_id ON link (rhs_file_id);


CREATE OR REPLACE VIEW statistics AS
select tsr.user_id, sum(size_in_byte) totalVolume, count(*) totalCount, max(created_on) lastUploaded, min(created_on) firstUploaded,
	(select count(*) from catalog_text_search tsr1 where 'dls:internal' NOT IN (select name from file_meta fm where tsr1.id = fm.file_id) and deleted = true and tsr.user_id = tsr1.user_id) totalDeleted,
	(select count(*) from catalog_text_search tsr2 where external = true and tsr.user_id = tsr2.user_id) totalExternal,
	(select count(*) from catalog_text_search tsr3 where uploaded = false and tsr.user_id = tsr3.user_id) totalFailed,
	(select count(*) from file_share tsr4 where tsr.user_id = tsr4.user_id) totalShared,
	(select count(*) from file tsr5 where bundled = true and tsr.user_id = tsr5.user_id) totalBundled
	from catalog_text_search tsr where 'dls:internal' NOT IN (select name from file_meta fm where tsr.id = fm.file_id) group by tsr.user_id ;

alter view statistics owner to dlsusr;
ALTER TABLE file_share ADD COLUMN shared_on TIMESTAMP WITHOUT TIME ZONE;
update file set deleted = false where deleted is null;
update file set external = false where external is null;
update file set bundled = false where bundled is null;
