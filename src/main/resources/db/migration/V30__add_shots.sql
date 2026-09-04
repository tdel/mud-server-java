ALTER TABLE item ADD COLUMN quantity INT NOT NULL DEFAULT 1;
ALTER TABLE character ADD COLUMN active_soulshot_grade VARCHAR(10) NULL;
ALTER TABLE character ADD COLUMN active_spiritshot_grade VARCHAR(10) NULL;
