ALTER TABLE family ADD CONSTRAINT uq_family_responsible_member UNIQUE (responsible_member_id);

CREATE TABLE member_ministry (
    member_id UUID NOT NULL REFERENCES member(id) ON DELETE CASCADE,
    ministry_id UUID NOT NULL REFERENCES ministry(id) ON DELETE CASCADE,
    joined_at TIMESTAMP NOT NULL DEFAULT now(),
    PRIMARY KEY (member_id, ministry_id)
);
