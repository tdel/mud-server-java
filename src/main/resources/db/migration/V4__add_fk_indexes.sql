CREATE INDEX idx_character_account_id ON character (account_id);
CREATE INDEX idx_item_character_id ON item (character_id);
CREATE INDEX idx_item_room_id ON item (room_id);
CREATE INDEX idx_item_template_id ON item (template_id);
