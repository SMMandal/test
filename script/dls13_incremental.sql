ALTER TABLE directory_meta ADD COLUMN is_meta BOOLEAN DEFAULT false;
DROP INDEX uk_directory_meta;
CREATE UNIQUE INDEX uk_directory_meta ON public.directory_meta USING btree (directory_id, lower((name)::text), is_meta);