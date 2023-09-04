create database if not exists test character set utf8mb4;
use test;

create table if not exists t1
(
    a int(1),
    b int(1),
    c int(1),
    d int(1),
    e varchar(50)
);


select *
from t1;

insert into t1 values (1, 1, 1, 1, '1');


delete
from t1;





