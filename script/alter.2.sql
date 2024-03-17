drop view statistics;
drop table statistics;
CREATE OR REPLACE VIEW statistics AS 
select tsr.user_id, sum(size_in_byte) totalVolume, count(*) totalCount, 
	max(created_on) lastUploaded, min(created_on) firstUploaded, 
	(select count(*) from catalog_text_search tsr1 where deleted = true and tsr.user_id = tsr1.user_id) totalDeleted,
	(select count(*) from catalog_text_search tsr2 where external = true and tsr.user_id = tsr2.user_id) totalExternal,
	(select count(*) from catalog_text_search tsr3 where uploaded = false and tsr.user_id = tsr3.user_id) totalFailed,
	(select count(*) from file_share tsr4 where tsr.user_id = tsr4.user_id) totalShared,
	(select count(*) from file tsr5 where bundled = true and tsr.user_id = tsr5.user_id) totalBundled
	from catalog_text_search tsr group by user_id;
alter view statistics owner to dlsusr;
