DROP TABLE catalog;

DROP TRIGGER trigger_catalog_file_insert ON file;

DROP TRIGGER trigger_catalog_file_update ON file;

DROP TRIGGER trigger_catalog_file_meta_insert ON file_meta;

DROP TRIGGER trigger_catalog_file_meta_delete ON file_meta;

DROP TRIGGER trigger_catalog_directory_insert ON directory;

DROP TRIGGER trigger_catalog_directory_delete ON directory;

DROP TRIGGER trigger_catalog_directory_meta_insert ON directory_meta;

DROP TRIGGER trigger_catalog_directory_meta_delete ON directory_meta;

DROP TRIGGER trigger_catalog_permission_insert ON permission;

DROP TRIGGER trigger_catalog_permission_delete ON permission;


CREATE TABLE IF NOT EXISTS catalog (
 id bigint primary key,
 type bpchar,
 name text,
 path text,
 parent text,
 size bigint,
 created_on timestamp without time zone,
 created_by TEXT,
 savepoint text,
 uploaded boolean,
 upload_status text,
 lock bpchar,
 bundled boolean,
 qualifier text[],
 shared_to text[],
 permitted_users bigint[],
 metadata_ids bigint[],
 metadata_json jsonb,
 metadata jsonb,
 tenant_id integer  );



ALTER TABLE catalog OWNER TO dlsusr;



DELETE FROM catalog;

INSERT INTO catalog (id, type, name, path, parent, size, created_on, created_by, savepoint, uploaded, upload_status, lock, bundled, qualifier, shared_to, permitted_users, metadata_ids, metadata_json, tenant_id)
    SELECT id, type, name, path, parent, size, created_on, created_by, savepoint, uploaded, upload_status, lock, bundled, qualifier, shared_to, permitted_users, metadata_ids, metadata_json, tenant_id FROM explorer_view;


CREATE OR REPLACE VIEW temp_catalog1 AS SELECT file_id, json_object(ARRAY_AGG(ltrim(substring(name,position('@' in name)),'@')),ARRAY_AGG(value)) metadata FROM file_meta WHERE name NOT LIKE 'dls:%' GROUP BY file_id;

UPDATE catalog SET metadata = (SELECT metadata FROM temp_catalog1 WHERE temp_catalog1.file_id = catalog.id) WHERE type='F';
DROP VIEW temp_catalog1;

CREATE OR REPLACE VIEW temp_catalog2 AS SELECT directory_id, json_object(ARRAY_AGG(ltrim(substring(name,position('@' in name)),'@')),ARRAY_AGG(value)) metadata FROM directory_meta WHERE name NOT LIKE 'dls:%' GROUP BY directory_id;

UPDATE catalog SET metadata = (SELECT metadata FROM temp_catalog2 WHERE temp_catalog2.directory_id = catalog.id) WHERE type='D';
DROP VIEW temp_catalog2;


ALTER TABLE catalog ADD COLUMN file_count integer;
ALTER TABLE catalog ADD COLUMN subdir_count integer;

CREATE INDEX catalog_parent ON catalog (parent);

UPDATE catalog SET file_count = coalesce(
    (SELECT COUNT(*) FROM file WHERE catalog.id = file.directory_id AND file.deleted <> true GROUP BY directory_id), 0)
    WHERE type = 'D';

UPDATE catalog SET metadata_ids = NULL, metadata_json = NULL WHERE metadata_ids='{}';

-- function to insert new file

CREATE OR REPLACE FUNCTION func_catalog_file_insert() RETURNS TRIGGER LANGUAGE PLPGSQL AS $$

DECLARE dlsUser TEXT;
DECLARE dir TEXT;
DECLARE permittedUser BIGINT[];

BEGIN
    IF ( NEW.deleted IS NULL OR NEW.deleted = false ) AND NEW.fs_path LIKE '%' || NEW.file_name THEN
        SELECT dls_user INTO dlsUser FROM users WHERE id = NEW.user_id;
        IF NEW.directory_id IS NOT NULL THEN
            SELECT directory INTO dir FROM directory WHERE id = NEW.directory_id;
            SELECT ARRAY_AGG(permitted_user) INTO permittedUser FROM permission
                WHERE directory_id = NEW.directory_id GROUP BY directory_id;
        ELSE
            permittedUser[1] = NEW.user_id;
        END IF;

        INSERT INTO catalog(id, type, name, path, parent, size, created_on,
            created_by, savepoint, uploaded, upload_status, lock, bundled,
            qualifier, shared_to, permitted_users)
        VALUES(NEW.id, 'F', NEW.file_name, NEW.fs_path, dir,
            NEW.size_in_byte, NEW.created_on, dlsUser, NEW.savepoint, NEW.uploaded,
            NEW.upload_status, NEW.lock, NEW.bundled, NEW.qualifier, NEW.shared_to, permittedUser);

        UPDATE catalog SET file_count = file_count + 1 WHERE id = (SELECT ID FROM catalog WHERE path = dir);
    END IF;
RETURN NEW;
END;
$$  ;



-- function to update / delete existing file


CREATE OR REPLACE FUNCTION func_catalog_file_update()
  RETURNS TRIGGER
  LANGUAGE PLPGSQL
  AS
$$

DECLARE user TEXT;
DECLARE dir TEXT;
DECLARE permittedUser BIGINT[];

BEGIN
    IF NEW.uploaded = 'true'::boolean AND ( OLD.uploaded = 'false'::boolean OR OLD.uploaded IS NULL) THEN
    	UPDATE catalog SET uploaded = NEW.uploaded WHERE ID = OLD.id;
    END IF;

    SELECT directory INTO dir FROM directory WHERE id = NEW.directory_id;

    IF NEW.deleted <> OLD.deleted AND NEW.deleted = true THEN
    	DELETE FROM catalog WHERE ID = OLD.id;
    	UPDATE catalog SET file_count = file_count - 1 WHERE id = (SELECT ID FROM catalog WHERE path = dir);
    END IF;

    IF NEW.savepoint <> OLD.savepoint THEN
    	UPDATE catalog SET savepoint = OLD.savepoint WHERE ID = OLD.id;
    END IF;

    IF NEW.lock <> OLD.lock THEN
    	UPDATE catalog SET lock = NEW.lock WHERE ID = OLD.id;
    END IF;

    IF NEW.directory_id <> OLD.directory_id THEN
	    IF NEW.directory_id <> NULL THEN
	    	SELECT ARRAY_AGG(permitted_user) INTO permittedUser FROM permission
	    		WHERE directory_id = NEW.directory_id GROUP BY directory_id;
	    ELSE
	    	permittedUser[1] = NEW.user_id;
	    END IF;
    	UPDATE catalog SET parent = dir, permitted_users = permittedUser WHERE ID = OLD.id;
    END IF;

RETURN NEW;
END;
$$  ;


-- function to insert new file metadata

CREATE OR REPLACE FUNCTION func_catalog_file_meta_insert() RETURNS TRIGGER LANGUAGE PLPGSQL AS $$

--DECLARE dlsUser TEXT;
DECLARE jsondata JSONB;
DECLARE jsonmetadata JSONB;
DECLARE arraydata BIGINT ARRAY;
BEGIN
    IF NEW.name NOT LIKE 'dls:%' THEN
--        SELECT dls_user INTO dlsUser FROM users WHERE id = NEW.user_id;
        SELECT JSONB_AGG(json_strip_nulls(json_build_object(
--        		'name',ltrim(name,'1234567890@'),
        		'name',ltrim(substring(name,position('@' in name)),'@'),
        		'value', value,
        		'createdBy',user_id,
        		'privateTo',
        		CASE
        	     		WHEN name ~ '\d+@.+'
        	     			THEN user_id
        	     		ELSE NULL
        	     	END
             	))) INTO jsondata FROM file_meta WHERE file_id = NEW.file_id AND name NOT LIKE 'dls:%';

        SELECT json_object(ARRAY_AGG(ltrim(substring(name,position('@' in name)),'@')),ARRAY_AGG(value))
            INTO jsonmetadata FROM file_meta WHERE file_id = NEW.file_id AND name NOT LIKE 'dls:%';

        SELECT ARRAY_AGG(id) INTO arraydata FROM file_meta WHERE file_id = NEW.file_id AND name NOT LIKE 'dls:%';
--        SELECT json_agg(json_build_object('name', NEW.name, 'value', NEW.value, 'createdBy', NEW.user_id)) INTO jsondata;
        UPDATE catalog SET metadata_ids = arraydata, metadata_json = jsondata::jsonb, metadata = jsonmetadata::jsonb WHERE id = NEW.file_id;
    END IF;
RETURN NEW;
END;
$$  ;




-- function to remove file metadata

CREATE OR REPLACE FUNCTION func_catalog_file_meta_delete() RETURNS TRIGGER LANGUAGE PLPGSQL AS $$

--DECLARE dlsUser TEXT;
DECLARE jsondata JSONB;
DECLARE arraydata BIGINT ARRAY;
DECLARE jsonmetadata JSONB;
BEGIN
--    IF NEW.name NOT LIKE 'dls:%' THEN
--        SELECT dls_user INTO dlsUser FROM users WHERE id = NEW.user_id;
        SELECT JSONB_AGG(json_strip_nulls(json_build_object(
--        		'name',ltrim(name,'1234567890@'),
        		'name',ltrim(substring(name,position('@' in name)),'@'),
        		'value', value,
        		'createdBy',user_id,
        		'privateTo',
        		CASE
        	     		WHEN name ~ '\d+@.+'
        	     			THEN user_id
        	     		ELSE NULL
        	     	END
             	))) INTO jsondata FROM file_meta WHERE file_id = OLD.file_id AND name NOT LIKE 'dls:%';

        SELECT json_object(ARRAY_AGG(ltrim(substring(name,position('@' in name)),'@')),ARRAY_AGG(value))
            INTO jsonmetadata FROM file_meta WHERE file_id = OLD.file_id AND name NOT LIKE 'dls:%';

        SELECT ARRAY_AGG(id) INTO arraydata FROM file_meta WHERE file_id = OLD.file_id AND name NOT LIKE 'dls:%';
--        SELECT json_agg(json_build_object('name', NEW.name, 'value', NEW.value, 'createdBy', NEW.user_id)) INTO jsondata;
        UPDATE catalog SET metadata_ids = arraydata, metadata = jsonmetadata::jsonb, metadata_json = jsondata::jsonb WHERE id = OLD.file_id;
--    END IF;
RETURN NEW;
END;
$$  ;


-- function to insert new directory

CREATE OR REPLACE FUNCTION func_catalog_directory_insert()  RETURNS TRIGGER LANGUAGE PLPGSQL AS $$

DECLARE dlsUser TEXT;
DECLARE permittedUser BIGINT[];
DECLARE parentDir TEXT;
BEGIN

    SELECT dls_user INTO dlsUser FROM users WHERE id = NEW.created_by_id;

    SELECT ARRAY_AGG(permitted_user) INTO permittedUser FROM permission
            WHERE directory_id = NEW.id AND action LIKE '%A%' GROUP BY directory_id;

    parentDir := RTRIM((regexp_match(NEW.directory, '/.*/'))[1], '/');

    INSERT INTO catalog(id, type, name, path, parent, file_count, created_on,
        created_by, savepoint, uploaded, upload_status, lock, bundled,
        qualifier, shared_to, permitted_users, tenant_id)
    VALUES(NEW.id, 'D', regexp_replace(NEW.directory,'.*/','') , NEW.directory,
        parentDir,
        0, NEW.created_on, dlsUser, NULL, true,
        NULL, NULL, NULL, NULL, NULL, permittedUser, NEW.tenant_id);

    UPDATE catalog set subdir_count = (SELECT COUNT(*) from catalog where type='D' AND parent = parentDir AND tenant_id = NEW.tenant_id) WHERE path = parentDir AND tenant_id = NEW.tenant_id;

RETURN NEW;
END;
$$  ;


-- function to delete existing directory

CREATE OR REPLACE FUNCTION func_catalog_directory_delete()  RETURNS TRIGGER LANGUAGE PLPGSQL  AS $$
DECLARE parentDir TEXT;
BEGIN
    SELECT directory INTO parentDir FROM directory WHERE tenant_id = OLD.tenant_id AND id = OLD.parent;
    DELETE FROM catalog WHERE ID = OLD.id;
    UPDATE catalog set subdir_count = (SELECT COUNT(*) from catalog where type='D' AND parent = parentDir AND tenant_id = OLD.tenant_id) WHERE path = parentDir AND tenant_id = OLD.tenant_id;
RETURN NEW;
END;
$$  ;



-- function to insert new directory metadata

CREATE OR REPLACE FUNCTION func_catalog_directory_meta_insert() RETURNS TRIGGER LANGUAGE PLPGSQL AS $$

DECLARE jsondata JSONB;
DECLARE jsonmetadata JSONB;
DECLARE arraydata BIGINT ARRAY;
BEGIN
    IF  NEW.is_meta <> false THEN
        SELECT JSONB_AGG(json_strip_nulls(json_build_object(
        		'name', name,
        		'value', value,
        		'createdBy',user_id
             	))) INTO jsondata FROM directory_meta WHERE directory_id = NEW.directory_id AND is_meta <> false;
        SELECT ARRAY_AGG(id) INTO arraydata FROM directory_meta WHERE directory_id = NEW.directory_id AND is_meta <> false;

        SELECT json_object(ARRAY_AGG(ltrim(substring(name,position('@' in name)),'@')),ARRAY_AGG(value))
            INTO jsonmetadata FROM directory_meta WHERE directory_id = NEW.directory_id AND name NOT LIKE 'dls:%';

        UPDATE catalog SET metadata_ids = arraydata, metadata =  jsonmetadata::jsonb, metadata_json =  jsondata::jsonb WHERE id = NEW.directory_id;
    END IF;
RETURN NEW;
END;
$$  ;


-- function to remove new directory metadata

CREATE OR REPLACE FUNCTION func_catalog_directory_meta_delete()  RETURNS TRIGGER  LANGUAGE PLPGSQL  AS $$

DECLARE jsondata JSONB;
DECLARE jsonmetadata JSONB;
DECLARE arraydata BIGINT ARRAY;
BEGIN
    IF OLD.is_meta <> false THEN
        SELECT JSONB_AGG(json_strip_nulls(json_build_object(
        		'name', name,
        		'value', value,
        		'createdBy',user_id
             	))) INTO jsondata FROM directory_meta WHERE directory_id = OLD.directory_id AND is_meta <> false ;
        SELECT ARRAY_AGG(id) INTO arraydata FROM directory_meta WHERE directory_id = OLD.directory_id AND is_meta <> false ;
        SELECT json_object(ARRAY_AGG(ltrim(substring(name,position('@' in name)),'@')),ARRAY_AGG(value))
            INTO jsonmetadata FROM directory_meta WHERE directory_id = OLD.directory_id AND name NOT LIKE 'dls:%';
        UPDATE catalog SET metadata_ids = arraydata, metadata =  jsonmetadata::jsonb, metadata_json =  jsondata::jsonb WHERE id = OLD.directory_id ;
    END IF;
RETURN NEW;
END;
$$  ;

-- function to insert new directory permission

CREATE OR REPLACE FUNCTION func_catalog_permission_insert() RETURNS TRIGGER LANGUAGE PLPGSQL AS $$

DECLARE permittedUser BIGINT[];

BEGIN

    SELECT ARRAY_AGG(permitted_user) INTO permittedUser FROM permission
            WHERE directory_id = NEW.directory_id AND action LIKE '%A%' GROUP BY directory_id;

    UPDATE catalog SET permitted_users = permittedUser WHERE id = NEW.directory_id;

RETURN NEW;
END;
$$  ;


-- function to delete new directory permission

CREATE OR REPLACE FUNCTION func_catalog_permission_delete() RETURNS TRIGGER LANGUAGE PLPGSQL AS $$

DECLARE permittedUser BIGINT[];

BEGIN

    SELECT ARRAY_AGG(permitted_user) INTO permittedUser FROM permission
            WHERE directory_id = OLD.directory_id AND action LIKE '%A%' GROUP BY directory_id;

    UPDATE catalog SET permitted_users = permittedUser WHERE id = OLD.directory_id;

RETURN NEW;
END;
$$  ;


CREATE TRIGGER trigger_catalog_file_insert AFTER INSERT ON file FOR EACH ROW
 EXECUTE PROCEDURE func_catalog_file_insert();

CREATE TRIGGER trigger_catalog_file_update AFTER UPDATE ON file FOR EACH ROW
 EXECUTE PROCEDURE func_catalog_file_update();

CREATE TRIGGER trigger_catalog_file_meta_insert AFTER INSERT OR UPDATE OR DELETE ON file_meta FOR EACH ROW
  EXECUTE PROCEDURE func_catalog_file_meta_insert();

CREATE TRIGGER trigger_catalog_file_meta_delete AFTER DELETE ON file_meta FOR EACH ROW
  EXECUTE PROCEDURE func_catalog_file_meta_delete();

CREATE TRIGGER trigger_catalog_directory_insert AFTER INSERT ON directory FOR EACH ROW
 EXECUTE PROCEDURE func_catalog_directory_insert();

CREATE TRIGGER trigger_catalog_directory_delete AFTER DELETE ON directory FOR EACH ROW
  EXECUTE PROCEDURE func_catalog_directory_delete();

CREATE TRIGGER trigger_catalog_directory_meta_insert AFTER INSERT OR UPDATE ON directory_meta FOR EACH ROW
  EXECUTE PROCEDURE func_catalog_directory_meta_insert();

CREATE TRIGGER trigger_catalog_directory_meta_delete AFTER DELETE ON directory_meta FOR EACH ROW
  EXECUTE PROCEDURE func_catalog_directory_meta_delete();

CREATE TRIGGER trigger_catalog_permission_insert AFTER INSERT ON permission FOR EACH ROW
 EXECUTE PROCEDURE func_catalog_permission_insert();

CREATE TRIGGER trigger_catalog_permission_delete AFTER DELETE ON permission FOR EACH ROW
 EXECUTE PROCEDURE func_catalog_permission_delete();



