CREATE TABLE IF NOT EXISTS RELATION_TYPE
(
    RELATION_TYPE TEXT NOT NULL,
    ID SERIAL PRIMARY KEY
);

CREATE TABLE IF NOT EXISTS DATASPACE
(
    ID SERIAL PRIMARY KEY,
    NAME TEXT NOT NULL,
    CONSTRAINT "UQ_NAME" UNIQUE (NAME)
);

CREATE TABLE IF NOT EXISTS SCHEMA_NODE
(
    SCHEMA_NODE_IDENTIFIER TEXT NOT NULL,
    ID SERIAL PRIMARY KEY
);

CREATE TABLE IF NOT EXISTS SCHEMA_SET
(
    ID SERIAL PRIMARY KEY,
    NAME TEXT NOT NULL,
    DATASPACE_ID BIGINT NOT NULL,
    UNIQUE (NAME, DATASPACE_ID),
    CONSTRAINT SCHEMA_SET_DATASPACE FOREIGN KEY (DATASPACE_ID) REFERENCES DATASPACE(ID) ON UPDATE CASCADE ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS YANG_RESOURCE
(
    ID SERIAL PRIMARY KEY,
    NAME TEXT NOT NULL,
    CONTENT TEXT NOT NULL,
    CHECKSUM TEXT NOT NULL,
    UNIQUE (CHECKSUM)
);

CREATE TABLE IF NOT EXISTS SCHEMA_SET_YANG_RESOURCES
(
    SCHEMA_SET_ID BIGINT NOT NULL,
    YANG_RESOURCE_ID BIGINT NOT NULL REFERENCES YANG_RESOURCE(ID),
    CONSTRAINT SCHEMA_SET_RESOURCE FOREIGN KEY (SCHEMA_SET_ID) REFERENCES SCHEMA_SET(ID) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS MODULE
(
    ID SERIAL PRIMARY KEY,
    NAMESPACE TEXT NOT NULL,
    REVISION TEXT NOT NULL,
    MODULE_CONTENT TEXT NOT NULL,
    DATASPACE_ID BIGINT NOT NULL,
    UNIQUE (DATASPACE_ID, NAMESPACE, REVISION),
    CONSTRAINT MODULE_DATASPACE FOREIGN KEY (DATASPACE_ID) REFERENCES DATASPACE (id) ON UPDATE CASCADE ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS FRAGMENT
(
    ID BIGSERIAL PRIMARY KEY,
    XPATH TEXT NOT NULL,
    ATTRIBUTES JSONB,
    ANCHOR_NAME TEXT,
    ANCHOR_ID BIGINT REFERENCES FRAGMENT(ID),
    PARENT_ID BIGINT REFERENCES FRAGMENT(ID),
    SCHEMA_SET_ID INTEGER REFERENCES SCHEMA_SET(ID),
    DATASPACE_ID INTEGER NOT NULL REFERENCES DATASPACE(ID),
    SCHEMA_NODE_ID INTEGER REFERENCES SCHEMA_NODE(ID),
    UNIQUE (DATASPACE_ID, ANCHOR_NAME, XPATH)
);

CREATE TABLE IF NOT EXISTS RELATION
(
    FROM_FRAGMENT_ID BIGINT NOT NULL REFERENCES FRAGMENT(ID),
    TO_FRAGMENT_ID   BIGINT NOT NULL REFERENCES FRAGMENT(ID),
    RELATION_TYPE_ID  INTEGER NOT NULL REFERENCES RELATION_TYPE(ID),
    FROM_REL_XPATH TEXT NOT NULL,
    TO_REL_XPATH TEXT NOT NULL,
    CONSTRAINT RELATION_PKEY PRIMARY KEY (TO_FRAGMENT_ID, FROM_FRAGMENT_ID, RELATION_TYPE_ID)
);

CREATE INDEX  IF NOT EXISTS "FKI_FRAGMENT_DATASPACE_ID_FK"     ON FRAGMENT USING BTREE(DATASPACE_ID) ;
CREATE INDEX  IF NOT EXISTS "FKI_FRAGMENT_SCHEMA_SET_ID_FK"    ON FRAGMENT USING BTREE(SCHEMA_SET_ID) ;
CREATE INDEX  IF NOT EXISTS "FKI_FRAGMENT_PARENT_ID_FK"        ON FRAGMENT USING BTREE(PARENT_ID) ;
CREATE INDEX  IF NOT EXISTS "FKI_FRAGMENT_ANCHOR_ID_FK"        ON FRAGMENT USING BTREE(ANCHOR_ID) ;
CREATE INDEX  IF NOT EXISTS "PERF_SCHEMA_NODE_SCHEMA_NODE_ID"  ON SCHEMA_NODE USING BTREE(SCHEMA_NODE_IDENTIFIER) ;
CREATE INDEX  IF NOT EXISTS "FKI_SCHEMA_NODE_ID_TO_ID"         ON FRAGMENT USING BTREE(SCHEMA_NODE_ID) ;
CREATE INDEX  IF NOT EXISTS "FKI_RELATION_TYPE_ID_FK"          ON RELATION USING BTREE(RELATION_TYPE_ID);
CREATE INDEX  IF NOT EXISTS "FKI_RELATIONS_FROM_ID_FK"         ON RELATION USING BTREE(FROM_FRAGMENT_ID);
CREATE INDEX  IF NOT EXISTS "FKI_RELATIONS_TO_ID_FK"           ON RELATION USING BTREE(TO_FRAGMENT_ID);
CREATE INDEX  IF NOT EXISTS "PERF_MODULE_MODULE_CONTENT"       ON MODULE USING BTREE(MODULE_CONTENT);
CREATE UNIQUE INDEX  IF NOT EXISTS "UQ_FRAGMENT_XPATH"ON FRAGMENT USING btree(xpath COLLATE pg_catalog."default" text_pattern_ops, dataspace_id);
