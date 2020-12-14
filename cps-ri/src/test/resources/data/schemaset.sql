INSERT INTO DATASPACE (ID, NAME) VALUES
    (1001, 'DATASPACE-001'), (1002, 'DATASPACE-002');

INSERT INTO SCHEMA_SET (ID, NAME, DATASPACE_ID) VALUES
    (2001, 'SCHEMA-SET-001', 1001), (2002, 'SCHEMA-SET-002', 1001);

INSERT INTO YANG_RESOURCE (ID, NAME, CONTENT, CHECKSUM) VALUES
    (3001, 'module1@2020-02-02.yang', 'CONTENT-001', '877e65a9f36d54e7702c3f073f6bc42b'),
    (3002, 'module2@2020-02-02.yang', 'CONTENT-002', '88892586b1f23fe8c1595759784a18f8'),
    (3003, 'module3@2020-02-02.yang', 'CONTENT-003', 'fc5740499a09a48e0c95d6fc45d4bde8'),
    (3004, 'module4@2020-02-02.yang', 'CONTENT-004', '3801280fe532f5cbf535695cf6122026');

INSERT INTO SCHEMA_SET_YANG_RESOURCES (SCHEMA_SET_ID, YANG_RESOURCE_ID) VALUES
    (2001, 3001), (2001, 3002), (2002, 3003), (2002, 3004);
