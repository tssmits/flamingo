CREATE EXTENSION postgis;

DROP TABLE IF EXISTS public.meaningless_unittest_table;

CREATE TABLE public.meaningless_unittest_table (
    id serial constraint meaningless_unittest_table_pk primary key,
    codeword character varying NOT NULL,
    amount integer DEFAULT 0 NOT NULL
);

SELECT AddGeometryColumn('public', 'meaningless_unittest_table', 'geom', 28992, 'POINT', 2);

ALTER TABLE public.meaningless_unittest_table OWNER TO flamingo;

INSERT INTO public.meaningless_unittest_table (id, codeword, amount, geom) VALUES (1, 'alpha', 1, NULL);
INSERT INTO public.meaningless_unittest_table (id, codeword, amount, geom) VALUES (2, 'bravo', 2, NULL);
INSERT INTO public.meaningless_unittest_table (id, codeword, amount, geom) VALUES (3, 'charlie', 3, NULL);
INSERT INTO public.meaningless_unittest_table (id, codeword, amount, geom) VALUES (4, 'delta', 4, NULL);
INSERT INTO public.meaningless_unittest_table (id, codeword, amount, geom) VALUES (5, 'echo', 5, NULL);
INSERT INTO public.meaningless_unittest_table (id, codeword, amount, geom) VALUES (6, 'foxtrot', 6, NULL);
INSERT INTO public.meaningless_unittest_table (id, codeword, amount, geom) VALUES (7, 'golf', 7, NULL);
INSERT INTO public.meaningless_unittest_table (id, codeword, amount, geom) VALUES (8, 'hotel', 8, NULL);
INSERT INTO public.meaningless_unittest_table (id, codeword, amount, geom) VALUES (9, 'india', 9, NULL);
INSERT INTO public.meaningless_unittest_table (id, codeword, amount, geom) VALUES (10, 'juliet', 10, NULL);
INSERT INTO public.meaningless_unittest_table (id, codeword, amount, geom) VALUES (11, 'kilo', 11, NULL);
INSERT INTO public.meaningless_unittest_table (id, codeword, amount, geom) VALUES (12, 'lima', 12, NULL);
INSERT INTO public.meaningless_unittest_table (id, codeword, amount, geom) VALUES (13, 'mike', 13, NULL);
INSERT INTO public.meaningless_unittest_table (id, codeword, amount, geom) VALUES (14, 'november', 14, NULL);
INSERT INTO public.meaningless_unittest_table (id, codeword, amount, geom) VALUES (15, 'oscar', 15, NULL);
INSERT INTO public.meaningless_unittest_table (id, codeword, amount, geom) VALUES (16, 'papa', 16, NULL);
INSERT INTO public.meaningless_unittest_table (id, codeword, amount, geom) VALUES (17, 'quebec', 17, NULL);
INSERT INTO public.meaningless_unittest_table (id, codeword, amount, geom) VALUES (18, 'romeo', 18, NULL);
INSERT INTO public.meaningless_unittest_table (id, codeword, amount, geom) VALUES (19, 'sierra', 19, NULL);
INSERT INTO public.meaningless_unittest_table (id, codeword, amount, geom) VALUES (20, 'tango', 20, NULL);
INSERT INTO public.meaningless_unittest_table (id, codeword, amount, geom) VALUES (21, 'uniform', 21, NULL);
INSERT INTO public.meaningless_unittest_table (id, codeword, amount, geom) VALUES (22, 'victor', 22, NULL);
INSERT INTO public.meaningless_unittest_table (id, codeword, amount, geom) VALUES (23, 'whiskey', 23, NULL);
INSERT INTO public.meaningless_unittest_table (id, codeword, amount, geom) VALUES (24, 'x-ray', 24, NULL);
INSERT INTO public.meaningless_unittest_table (id, codeword, amount, geom) VALUES (25, 'yankee', 25, NULL);
INSERT INTO public.meaningless_unittest_table (id, codeword, amount, geom) VALUES (26, 'zulu', 26, NULL);
