PRAGMA foreign_keys = ON;

-- Normalize broken parent relations accumulated in earlier versions.
UPDATE tag
SET parent_tag_id = NULL
WHERE parent_tag_id = ''
   OR parent_tag_id = id;

UPDATE tag
SET parent_tag_id = NULL
WHERE parent_tag_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM tag p WHERE p.id = tag.parent_tag_id
  );

-- Flatten accidental 3+ depth chains to at most two levels.
UPDATE tag
SET parent_tag_id = (
    SELECT CASE
             WHEN p.parent_tag_id IS NULL THEN tag.parent_tag_id
             ELSE p.parent_tag_id
           END
    FROM tag p
    WHERE p.id = tag.parent_tag_id
)
WHERE parent_tag_id IS NOT NULL
  AND EXISTS (
      SELECT 1 FROM tag p WHERE p.id = tag.parent_tag_id AND p.parent_tag_id IS NOT NULL
  );

-- Recompute level based on parent relation.
UPDATE tag
SET level = CASE WHEN parent_tag_id IS NULL THEN 1 ELSE 2 END;

-- Child tags should follow parent scope unless parent is global.
UPDATE tag
SET scope = (
    SELECT p.scope FROM tag p WHERE p.id = tag.parent_tag_id
)
WHERE parent_tag_id IS NOT NULL
  AND EXISTS (
      SELECT 1 FROM tag p WHERE p.id = tag.parent_tag_id AND p.scope <> 'global'
  );
