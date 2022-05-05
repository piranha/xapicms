alter table webhook_log add column post_uuid uuid references posts (uuid);
