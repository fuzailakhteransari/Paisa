CREATE TABLE `paisa_chunks` (
	`upload_id` text NOT NULL,
	`part` integer NOT NULL,
	`data` text NOT NULL,
	PRIMARY KEY(`upload_id`, `part`)
);
--> statement-breakpoint
CREATE TABLE `paisa_heads` (
	`uid` text PRIMARY KEY NOT NULL,
	`revision` integer NOT NULL,
	`upload_id` text,
	`parts` integer NOT NULL,
	`hash` text NOT NULL,
	`bytes` integer NOT NULL,
	`updated` integer NOT NULL,
	`entry_count` integer NOT NULL
);
--> statement-breakpoint
CREATE TABLE `paisa_limits` (
	`key` text PRIMARY KEY NOT NULL,
	`hits` integer NOT NULL,
	`expires` integer NOT NULL
);
--> statement-breakpoint
CREATE INDEX `limits_expiry` ON `paisa_limits` (`expires`);--> statement-breakpoint
CREATE TABLE `paisa_pairs` (
	`id` text PRIMARY KEY NOT NULL,
	`challenge` text NOT NULL,
	`code` text NOT NULL,
	`device` text NOT NULL,
	`expires` integer NOT NULL,
	`approved_uid` text,
	`approved_email` text,
	`claimed_hash` text
);
--> statement-breakpoint
CREATE INDEX `pairs_expiry` ON `paisa_pairs` (`expires`);--> statement-breakpoint
CREATE TABLE `paisa_revisions` (
	`uid` text NOT NULL,
	`revision` integer NOT NULL,
	`upload_id` text NOT NULL,
	`parts` integer NOT NULL,
	`hash` text NOT NULL,
	`bytes` integer NOT NULL,
	`updated` integer NOT NULL,
	`entry_count` integer NOT NULL,
	PRIMARY KEY(`uid`, `revision`)
);
--> statement-breakpoint
CREATE TABLE `paisa_sessions` (
	`hash` text PRIMARY KEY NOT NULL,
	`uid` text NOT NULL,
	`email` text NOT NULL,
	`kind` text NOT NULL,
	`expires` integer NOT NULL
);
--> statement-breakpoint
CREATE INDEX `sessions_uid` ON `paisa_sessions` (`uid`);--> statement-breakpoint
CREATE INDEX `sessions_expiry` ON `paisa_sessions` (`expires`);--> statement-breakpoint
CREATE TABLE `paisa_uploads` (
	`id` text PRIMARY KEY NOT NULL,
	`uid` text NOT NULL,
	`base_revision` integer NOT NULL,
	`parts` integer NOT NULL,
	`hash` text NOT NULL,
	`bytes` integer NOT NULL,
	`entry_count` integer NOT NULL,
	`created` integer NOT NULL,
	`committed` integer NOT NULL
);
--> statement-breakpoint
CREATE INDEX `uploads_uid` ON `paisa_uploads` (`uid`);