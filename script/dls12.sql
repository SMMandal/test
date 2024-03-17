--
-- PostgreSQL database dump
--

-- Dumped from database version 12.6 (Ubuntu 12.6-0ubuntu0.20.04.1)
-- Dumped by pg_dump version 12.6 (Ubuntu 12.6-0ubuntu0.20.04.1)

SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SELECT pg_catalog.set_config('search_path', '', false);
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;

SET default_tablespace = '';

SET default_table_access_method = heap;

--
-- Name: audit; Type: TABLE; Schema: public; Owner: dlsusr
--

CREATE TABLE public.audit (
    id bigint NOT NULL,
    entity character varying(255) NOT NULL,
    entity_id bigint NOT NULL,
    event character varying(255) NOT NULL,
    success boolean NOT NULL,
    event_time timestamp without time zone NOT NULL,
    user_id bigint NOT NULL,
    provenance text
);


ALTER TABLE public.audit OWNER TO dlsusr;

--
-- Name: catalog_text_search; Type: VIEW; Schema: public; Owner: dlsusr
--

CREATE VIEW public.catalog_text_search AS
SELECT
    NULL::bigint AS id,
    NULL::json AS bundle,
    NULL::boolean AS bundled,
    NULL::timestamp without time zone AS created_on,
    NULL::boolean AS deleted,
    NULL::timestamp without time zone AS deleted_on,
    NULL::boolean AS external,
    NULL::character varying(255) AS file_name,
    NULL::character varying(255) AS fs_path,
    NULL::character(2) AS lock,
    NULL::character varying(50)[] AS qualifier,
    NULL::character varying(255) AS savepoint,
    NULL::character varying(2) AS storage,
    NULL::character varying(255)[] AS shared_to,
    NULL::bigint AS size_in_byte,
    NULL::boolean AS uploaded,
    NULL::character varying(255) AS upload_status,
    NULL::bigint AS user_id,
    NULL::bigint[] AS acquired_user,
    NULL::character varying(10) AS action,
    NULL::bigint AS directory_id,
    NULL::tsvector AS document;


ALTER TABLE public.catalog_text_search OWNER TO dlsusr;

--
-- Name: comment; Type: TABLE; Schema: public; Owner: dlsusr
--

CREATE TABLE public.comment (
    id bigint NOT NULL,
    comment character varying(255),
    created_on timestamp without time zone,
    file_id bigint,
    tenant_id bigint,
    user_id bigint
);


ALTER TABLE public.comment OWNER TO dlsusr;

--
-- Name: directory; Type: TABLE; Schema: public; Owner: dlsusr
--

CREATE TABLE public.directory (
    id bigint NOT NULL,
    created_on timestamp without time zone,
    deleted boolean,
    deleted_on timestamp without time zone,
    directory character varying(255),
    enforcement_type character varying(255),
    parent bigint,
    created_by_id bigint,
    tenant_id bigint
);


ALTER TABLE public.directory OWNER TO dlsusr;

--
-- Name: directory_meta; Type: TABLE; Schema: public; Owner: dlsusr
--

CREATE TABLE public.directory_meta (
    id bigint NOT NULL,
    deleted boolean,
    deleted_on timestamp without time zone,
    name character varying(255),
    type character varying(255),
    value character varying(255),
    value_mandatory boolean,
    value_numeric double precision,
    directory_id bigint,
    user_id bigint,
    schema_id bigint
);


ALTER TABLE public.directory_meta OWNER TO dlsusr;

--
-- Name: file; Type: TABLE; Schema: public; Owner: dlsusr
--

CREATE TABLE public.file (
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
    deleted_on timestamp without time zone,
    bundle json,
    bundle_hash character varying(255),
    bundled boolean,
    lock character(2),
    qualifier character varying(50)[],
    shared_to character varying(255)[],
    storage character varying(2),
    upload_status character varying(255),
    directory_id bigint
);


ALTER TABLE public.file OWNER TO dlsusr;

--
-- Name: file_meta; Type: TABLE; Schema: public; Owner: dlsusr
--

CREATE TABLE public.file_meta (
    id bigint NOT NULL,
    name character varying(255),
    value character varying(255),
    file_id bigint,
    user_id bigint,
    qualifier character varying(50)[],
    value_numeric double precision,
    schema_id bigint
);


ALTER TABLE public.file_meta OWNER TO dlsusr;

--
-- Name: file_share; Type: TABLE; Schema: public; Owner: dlsusr
--

CREATE TABLE public.file_share (
    id bigint NOT NULL,
    file_id bigint,
    user_id bigint,
    shared_on timestamp without time zone
);


ALTER TABLE public.file_share OWNER TO dlsusr;

--
-- Name: hibernate_sequence; Type: SEQUENCE; Schema: public; Owner: dlsusr
--

CREATE SEQUENCE public.hibernate_sequence
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER TABLE public.hibernate_sequence OWNER TO dlsusr;

--
-- Name: link; Type: TABLE; Schema: public; Owner: dlsusr
--

CREATE TABLE public.link (
    id bigint NOT NULL,
    lhs_file_id bigint,
    relation character varying(255),
    rhs_file_id bigint,
    user_id bigint
);


ALTER TABLE public.link OWNER TO dlsusr;

--
-- Name: meta_schema; Type: TABLE; Schema: public; Owner: dlsusr
--

CREATE TABLE public.meta_schema (
    id bigint NOT NULL,
    deleted boolean,
    deleted_on timestamp without time zone,
    description character varying(255),
    name character varying(255) NOT NULL,
    phi boolean,
    type character varying(255) NOT NULL,
    tenant_id bigint NOT NULL
);


ALTER TABLE public.meta_schema OWNER TO dlsusr;

--
-- Name: permission; Type: TABLE; Schema: public; Owner: dlsusr
--

CREATE TABLE public.permission (
    id bigint NOT NULL,
    acquired_user bigint[],
    action character varying(10),
    permitted_user bigint,
    directory_id bigint,
    tenant_id bigint,
    user_id bigint
);


ALTER TABLE public.permission OWNER TO dlsusr;

--
-- Name: relation; Type: TABLE; Schema: public; Owner: dlsusr
--

CREATE TABLE public.relation (
    id bigint NOT NULL,
    deleted boolean,
    deleted_on timestamp without time zone,
    description character varying(255),
    relationship_name character varying(255) NOT NULL
);


ALTER TABLE public.relation OWNER TO dlsusr;

--
-- Name: statistics; Type: VIEW; Schema: public; Owner: dlsusr
--

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

--
-- Name: tenant; Type: TABLE; Schema: public; Owner: dlsusr
--

CREATE TABLE public.tenant (
    id bigint NOT NULL,
    api_key character varying(255) NOT NULL,
    tcup_user character varying(255) NOT NULL,
    admin boolean,
    allocated_storage bigint,
    allow_adhoc boolean,
    max_key_len integer,
    max_meta_per_file integer,
    max_value_len integer,
    organization character varying(255),
    schematic boolean,
    used_storage bigint
);


ALTER TABLE public.tenant OWNER TO dlsusr;

--
-- Name: users; Type: TABLE; Schema: public; Owner: dlsusr
--

CREATE TABLE public.users (
    id bigint NOT NULL,
    tenant_id bigint,
    dls_key character varying(255) NOT NULL,
    dls_user character varying(255) NOT NULL,
    admin boolean,
    last_updated_on timestamp without time zone,
    org_position text[],
    last_updated_by_id bigint
);


ALTER TABLE public.users OWNER TO dlsusr;

--
-- Name: audit audit_pkey; Type: CONSTRAINT; Schema: public; Owner: dlsusr
--

ALTER TABLE ONLY public.audit
    ADD CONSTRAINT audit_pkey PRIMARY KEY (id);


--
-- Name: comment comment_pkey; Type: CONSTRAINT; Schema: public; Owner: dlsusr
--

ALTER TABLE ONLY public.comment
    ADD CONSTRAINT comment_pkey PRIMARY KEY (id);


--
-- Name: directory_meta directory_meta_pkey; Type: CONSTRAINT; Schema: public; Owner: dlsusr
--

ALTER TABLE ONLY public.directory_meta
    ADD CONSTRAINT directory_meta_pkey PRIMARY KEY (id);


--
-- Name: directory directory_pkey; Type: CONSTRAINT; Schema: public; Owner: dlsusr
--

ALTER TABLE ONLY public.directory
    ADD CONSTRAINT directory_pkey PRIMARY KEY (id);


--
-- Name: file_meta file_meta_pkey; Type: CONSTRAINT; Schema: public; Owner: dlsusr
--

ALTER TABLE ONLY public.file_meta
    ADD CONSTRAINT file_meta_pkey PRIMARY KEY (id);


--
-- Name: file file_pkey; Type: CONSTRAINT; Schema: public; Owner: dlsusr
--

ALTER TABLE ONLY public.file
    ADD CONSTRAINT file_pkey PRIMARY KEY (id);


--
-- Name: file_share file_share_pkey; Type: CONSTRAINT; Schema: public; Owner: dlsusr
--

ALTER TABLE ONLY public.file_share
    ADD CONSTRAINT file_share_pkey PRIMARY KEY (id);


--
-- Name: link link_pkey; Type: CONSTRAINT; Schema: public; Owner: dlsusr
--

ALTER TABLE ONLY public.link
    ADD CONSTRAINT link_pkey PRIMARY KEY (id);


--
-- Name: meta_schema meta_schema_pkey; Type: CONSTRAINT; Schema: public; Owner: dlsusr
--

ALTER TABLE ONLY public.meta_schema
    ADD CONSTRAINT meta_schema_pkey PRIMARY KEY (id);


--
-- Name: permission permission_pkey; Type: CONSTRAINT; Schema: public; Owner: dlsusr
--

ALTER TABLE ONLY public.permission
    ADD CONSTRAINT permission_pkey PRIMARY KEY (id);


--
-- Name: relation relation_pkey; Type: CONSTRAINT; Schema: public; Owner: dlsusr
--

ALTER TABLE ONLY public.relation
    ADD CONSTRAINT relation_pkey PRIMARY KEY (id);


--
-- Name: tenant tenant_pkey; Type: CONSTRAINT; Schema: public; Owner: dlsusr
--

ALTER TABLE ONLY public.tenant
    ADD CONSTRAINT tenant_pkey PRIMARY KEY (id);


--
-- Name: file_share uk_access; Type: CONSTRAINT; Schema: public; Owner: dlsusr
--

ALTER TABLE ONLY public.file_share
    ADD CONSTRAINT uk_access UNIQUE (file_id, user_id);


--
-- Name: link uk_link; Type: CONSTRAINT; Schema: public; Owner: dlsusr
--

ALTER TABLE ONLY public.link
    ADD CONSTRAINT uk_link UNIQUE (lhs_file_id, relation, rhs_file_id);


--
-- Name: comment uk_timestamp; Type: CONSTRAINT; Schema: public; Owner: dlsusr
--

ALTER TABLE ONLY public.comment
    ADD CONSTRAINT uk_timestamp UNIQUE (created_on, user_id);


--
-- Name: users uniqdlsuser; Type: CONSTRAINT; Schema: public; Owner: dlsusr
--

ALTER TABLE ONLY public.users
    ADD CONSTRAINT uniqdlsuser UNIQUE (tenant_id, dls_user);


--
-- Name: tenant uniqkey; Type: CONSTRAINT; Schema: public; Owner: dlsusr
--

ALTER TABLE ONLY public.tenant
    ADD CONSTRAINT uniqkey UNIQUE (api_key);


--
-- Name: users uniqkey_users; Type: CONSTRAINT; Schema: public; Owner: dlsusr
--

ALTER TABLE ONLY public.users
    ADD CONSTRAINT uniqkey_users UNIQUE (tenant_id, dls_key);


--
-- Name: relation uniqrelationkey; Type: CONSTRAINT; Schema: public; Owner: dlsusr
--

ALTER TABLE ONLY public.relation
    ADD CONSTRAINT uniqrelationkey UNIQUE (relationship_name);


--
-- Name: tenant uniqtcupuser; Type: CONSTRAINT; Schema: public; Owner: dlsusr
--

ALTER TABLE ONLY public.tenant
    ADD CONSTRAINT uniqtcupuser UNIQUE (tcup_user);


--
-- Name: permission unique_permission; Type: CONSTRAINT; Schema: public; Owner: dlsusr
--

ALTER TABLE ONLY public.permission
    ADD CONSTRAINT unique_permission UNIQUE (permitted_user, directory_id);


--
-- Name: users user_pkey; Type: CONSTRAINT; Schema: public; Owner: dlsusr
--

ALTER TABLE ONLY public.users
    ADD CONSTRAINT user_pkey PRIMARY KEY (id);


--
-- Name: idx_file_fspath; Type: INDEX; Schema: public; Owner: dlsusr
--

CREATE INDEX idx_file_fspath ON public.file USING btree (fs_path);


--
-- Name: idx_link_lhs_file_id; Type: INDEX; Schema: public; Owner: dlsusr
--

CREATE INDEX idx_link_lhs_file_id ON public.link USING btree (lhs_file_id);


--
-- Name: idx_link_rhs_file_id; Type: INDEX; Schema: public; Owner: dlsusr
--

CREATE INDEX idx_link_rhs_file_id ON public.link USING btree (rhs_file_id);


--
-- Name: idx_meta_file_id; Type: INDEX; Schema: public; Owner: dlsusr
--

CREATE INDEX idx_meta_file_id ON public.file_meta USING btree (file_id);


--
-- Name: idx_meta_name; Type: INDEX; Schema: public; Owner: dlsusr
--

CREATE INDEX idx_meta_name ON public.file_meta USING btree (name);


--
-- Name: idx_meta_value; Type: INDEX; Schema: public; Owner: dlsusr
--

CREATE INDEX idx_meta_value ON public.file_meta USING btree (value);


--
-- Name: uk_directory; Type: INDEX; Schema: public; Owner: dlsusr
--

CREATE UNIQUE INDEX uk_directory ON public.directory USING btree (tenant_id, directory) WHERE (deleted_on IS NULL);


--
-- Name: uk_directory_deleted; Type: INDEX; Schema: public; Owner: dlsusr
--

CREATE UNIQUE INDEX uk_directory_deleted ON public.directory USING btree (tenant_id, directory, deleted_on) WHERE (deleted_on IS NOT NULL);


--
-- Name: uk_directory_meta; Type: INDEX; Schema: public; Owner: dlsusr
--

CREATE UNIQUE INDEX uk_directory_meta ON public.directory_meta USING btree (directory_id, lower((name)::text));


--
-- Name: uk_file; Type: INDEX; Schema: public; Owner: dlsusr
--

CREATE UNIQUE INDEX uk_file ON public.file USING btree (fs_path) WHERE (deleted_on IS NULL);


--
-- Name: uk_file_deleted; Type: INDEX; Schema: public; Owner: dlsusr
--

CREATE UNIQUE INDEX uk_file_deleted ON public.file USING btree (fs_path, deleted_on) WHERE (deleted_on IS NOT NULL);


--
-- Name: uk_schema; Type: INDEX; Schema: public; Owner: dlsusr
--

CREATE UNIQUE INDEX uk_schema ON public.meta_schema USING btree (tenant_id, lower((name)::text)) WHERE (deleted_on IS NULL);


--
-- Name: uk_schema_deleted; Type: INDEX; Schema: public; Owner: dlsusr
--

CREATE UNIQUE INDEX uk_schema_deleted ON public.meta_schema USING btree (tenant_id, lower((name)::text), deleted_on) WHERE (deleted_on IS NOT NULL);


--
-- Name: catalog_text_search _RETURN; Type: RULE; Schema: public; Owner: dlsusr
--

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


--
-- Name: comment comment_fk_file_id; Type: FK CONSTRAINT; Schema: public; Owner: dlsusr
--

ALTER TABLE ONLY public.comment
    ADD CONSTRAINT comment_fk_file_id FOREIGN KEY (file_id) REFERENCES public.file(id);


--
-- Name: comment comment_fk_tenant_id; Type: FK CONSTRAINT; Schema: public; Owner: dlsusr
--

ALTER TABLE ONLY public.comment
    ADD CONSTRAINT comment_fk_tenant_id FOREIGN KEY (tenant_id) REFERENCES public.tenant(id);


--
-- Name: comment comment_fk_user_id; Type: FK CONSTRAINT; Schema: public; Owner: dlsusr
--

ALTER TABLE ONLY public.comment
    ADD CONSTRAINT comment_fk_user_id FOREIGN KEY (user_id) REFERENCES public.users(id);


--
-- Name: directory_meta dir_schema_fk; Type: FK CONSTRAINT; Schema: public; Owner: dlsusr
--

ALTER TABLE ONLY public.directory_meta
    ADD CONSTRAINT dir_schema_fk FOREIGN KEY (schema_id) REFERENCES public.meta_schema(id) NOT VALID;


--
-- Name: directory directory_fk_created_by_id; Type: FK CONSTRAINT; Schema: public; Owner: dlsusr
--

ALTER TABLE ONLY public.directory
    ADD CONSTRAINT directory_fk_created_by_id FOREIGN KEY (created_by_id) REFERENCES public.users(id);


--
-- Name: directory directory_fk_tenant_id; Type: FK CONSTRAINT; Schema: public; Owner: dlsusr
--

ALTER TABLE ONLY public.directory
    ADD CONSTRAINT directory_fk_tenant_id FOREIGN KEY (tenant_id) REFERENCES public.tenant(id);


--
-- Name: directory_meta directory_meta_fk_directory_id; Type: FK CONSTRAINT; Schema: public; Owner: dlsusr
--

ALTER TABLE ONLY public.directory_meta
    ADD CONSTRAINT directory_meta_fk_directory_id FOREIGN KEY (directory_id) REFERENCES public.directory(id);


--
-- Name: directory_meta directory_meta_fk_user_id; Type: FK CONSTRAINT; Schema: public; Owner: dlsusr
--

ALTER TABLE ONLY public.directory_meta
    ADD CONSTRAINT directory_meta_fk_user_id FOREIGN KEY (user_id) REFERENCES public.users(id);


--
-- Name: file file_fk_directory_id; Type: FK CONSTRAINT; Schema: public; Owner: dlsusr
--

ALTER TABLE ONLY public.file
    ADD CONSTRAINT file_fk_directory_id FOREIGN KEY (directory_id) REFERENCES public.directory(id) NOT VALID;


--
-- Name: file file_fk_user_id; Type: FK CONSTRAINT; Schema: public; Owner: dlsusr
--

ALTER TABLE ONLY public.file
    ADD CONSTRAINT file_fk_user_id FOREIGN KEY (user_id) REFERENCES public.users(id) NOT VALID;


--
-- Name: file_meta file_meta_fk_file_id; Type: FK CONSTRAINT; Schema: public; Owner: dlsusr
--

ALTER TABLE ONLY public.file_meta
    ADD CONSTRAINT file_meta_fk_file_id FOREIGN KEY (file_id) REFERENCES public.file(id) NOT VALID;


--
-- Name: file_meta file_meta_fk_user_id; Type: FK CONSTRAINT; Schema: public; Owner: dlsusr
--

ALTER TABLE ONLY public.file_meta
    ADD CONSTRAINT file_meta_fk_user_id FOREIGN KEY (user_id) REFERENCES public.users(id) NOT VALID;


--
-- Name: file_meta file_schema_fk; Type: FK CONSTRAINT; Schema: public; Owner: dlsusr
--

ALTER TABLE ONLY public.file_meta
    ADD CONSTRAINT file_schema_fk FOREIGN KEY (schema_id) REFERENCES public.meta_schema(id) NOT VALID;


--
-- Name: file_share file_share_fk_file_id; Type: FK CONSTRAINT; Schema: public; Owner: dlsusr
--

ALTER TABLE ONLY public.file_share
    ADD CONSTRAINT file_share_fk_file_id FOREIGN KEY (file_id) REFERENCES public.file(id) NOT VALID;


--
-- Name: file_share file_share_fk_user_id; Type: FK CONSTRAINT; Schema: public; Owner: dlsusr
--

ALTER TABLE ONLY public.file_share
    ADD CONSTRAINT file_share_fk_user_id FOREIGN KEY (user_id) REFERENCES public.users(id) NOT VALID;


--
-- Name: audit fk_user_id; Type: FK CONSTRAINT; Schema: public; Owner: dlsusr
--

ALTER TABLE ONLY public.audit
    ADD CONSTRAINT fk_user_id FOREIGN KEY (user_id) REFERENCES public.users(id);


--
-- Name: link link_fk_lhs_file_id; Type: FK CONSTRAINT; Schema: public; Owner: dlsusr
--

ALTER TABLE ONLY public.link
    ADD CONSTRAINT link_fk_lhs_file_id FOREIGN KEY (lhs_file_id) REFERENCES public.file(id) NOT VALID;


--
-- Name: link link_fk_rhs_file_id; Type: FK CONSTRAINT; Schema: public; Owner: dlsusr
--

ALTER TABLE ONLY public.link
    ADD CONSTRAINT link_fk_rhs_file_id FOREIGN KEY (rhs_file_id) REFERENCES public.file(id) NOT VALID;


--
-- Name: link link_fk_user_id; Type: FK CONSTRAINT; Schema: public; Owner: dlsusr
--

ALTER TABLE ONLY public.link
    ADD CONSTRAINT link_fk_user_id FOREIGN KEY (user_id) REFERENCES public.users(id) NOT VALID;


--
-- Name: meta_schema meta_schema_fk_tenant_id; Type: FK CONSTRAINT; Schema: public; Owner: dlsusr
--

ALTER TABLE ONLY public.meta_schema
    ADD CONSTRAINT meta_schema_fk_tenant_id FOREIGN KEY (tenant_id) REFERENCES public.tenant(id);


--
-- Name: permission permission_fk_directory_id; Type: FK CONSTRAINT; Schema: public; Owner: dlsusr
--

ALTER TABLE ONLY public.permission
    ADD CONSTRAINT permission_fk_directory_id FOREIGN KEY (directory_id) REFERENCES public.directory(id);


--
-- Name: permission permission_fk_tenant_id; Type: FK CONSTRAINT; Schema: public; Owner: dlsusr
--

ALTER TABLE ONLY public.permission
    ADD CONSTRAINT permission_fk_tenant_id FOREIGN KEY (tenant_id) REFERENCES public.tenant(id);


--
-- Name: permission permission_fk_user_id; Type: FK CONSTRAINT; Schema: public; Owner: dlsusr
--

ALTER TABLE ONLY public.permission
    ADD CONSTRAINT permission_fk_user_id FOREIGN KEY (user_id) REFERENCES public.users(id);


--
-- Name: users users_fk_last_updated_by_id; Type: FK CONSTRAINT; Schema: public; Owner: dlsusr
--

ALTER TABLE ONLY public.users
    ADD CONSTRAINT users_fk_last_updated_by_id FOREIGN KEY (last_updated_by_id) REFERENCES public.users(id) NOT VALID;


--
-- Name: users users_fk_tenant_id; Type: FK CONSTRAINT; Schema: public; Owner: dlsusr
--

ALTER TABLE ONLY public.users
    ADD CONSTRAINT users_fk_tenant_id FOREIGN KEY (tenant_id) REFERENCES public.tenant(id) NOT VALID;


--
-- PostgreSQL database dump complete
--

