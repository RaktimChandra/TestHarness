DROP TABLE IF EXISTS fixture_lane;
CREATE TABLE fixture_lane (code VARCHAR(4) PRIMARY KEY, open BOOLEAN NOT NULL DEFAULT true);
INSERT INTO fixture_lane (code) VALUES ('L1');
INSERT INTO fixture_lane (code) VALUES ('L2');
