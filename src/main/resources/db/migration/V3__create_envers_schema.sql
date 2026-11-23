-- Generated from the Hibernate ORM / Envers mappings (MySQL dialect, schema export).
create table item_revisions (id bigint not null auto_increment, timestamp bigint not null, changed_by varchar(64) not null, primary key (id)) engine=InnoDB;
create table items_aud (revtype tinyint, created_at datetime(6), rev bigint not null, updated_at datetime(6), id binary(16) not null, owner_id binary(16), content TEXT, title varchar(255), primary key (rev, id)) engine=InnoDB;
alter table items_aud add constraint FKhimevh9s5mf19yihchoxdb46v foreign key (rev) references item_revisions (id);

-- History is read by item id, ordered by revision.
CREATE INDEX ix_items_aud_id_rev ON items_aud (id, rev);
