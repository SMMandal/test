CREATE TABLE audit (
	id bigint NOT NULL PRIMARY KEY,
	entity VARCHAR ( 255 ) NOT NULL,
	entity_id bigint NOT NULL,
	event VARCHAR ( 255 ) NOT NULL,
	success BOOLEAN NOT NULL,
	event_time TIMESTAMP NOT NULL,
        user_id bigint NOT NULL,
        provenance text,
        CONSTRAINT fk_user_id FOREIGN KEY (user_id) REFERENCES users(id) NOT VALID
);
alter table audit owner to dlsusr;