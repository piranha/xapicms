create table users (
  id serial primary key,
  created_at timestamptz default now() not null,
  updated_at timestamptz default now() not null,
  github text not null,
  email text not null,
  "name" text,
  access_token text not null,
  apikey text,
  repo text
);

create unique index users_github on users (github);


create table images (
  user_id int not null references users (id),
  id text not null,
  path text not null,
  hash text,
  created_at timestamptz default now() not null,
  updated_at timestamptz default now() not null,

  PRIMARY KEY(id, user_id)
);


create table posts (
  id text not null,
  user_id int not null references users (id),
  created_at timestamptz default now() not null,
  updated_at timestamptz default now() not null,
  published_at timestamptz,
  slug text not null,
  title text,
  uuid uuid,
  tags text[],
  status text,
  html text,
  
  primary key(user_id, id)
);

create unique index posts_uuid on posts (uuid);


create table post_log (
  id serial primary key,
  user_id int not null references users (id),
  created_at timestamptz default now() not null,
  request jsonb not null
);


create table webhook (
  id serial primary key,
  user_id int not null references users (id),
  created_at timestamptz default now() not null,
  type text not null, -- github or external
  url text not null, -- repo in case of github
  headers jsonb,
  enabled boolean not null default true
);

create table webhook_log (
  id serial primary key,
  webhook_id int not null references webhook (id),
  created_at timestamptz default now() not null,
  request jsonb not null,
  response jsonb
);
