-- Adds key skills to the seed test jobs (they were created before jobs had a skills field),
-- so match scores and "You have / Missing" on Best matches have something to compare.
-- Safe to run more than once. Spellings follow the app's skill list (resume/Skills.kt).
update public.jobs j set skills = v.skills
from (values
  ('Senior Android Developer',     '{Kotlin,Android,Jetpack Compose,Kotlin Coroutines,Hilt}'::text[]),
  ('Android Developer',            '{Kotlin,Android,Kotlin Coroutines,Retrofit,Room}'::text[]),
  ('DevOps Engineer',              '{AWS,Docker,Kubernetes,Terraform,CI/CD}'::text[]),
  ('Engineering Manager, Mobile',  '{Android,iOS,Agile}'::text[]),
  ('Backend Engineer (Java)',      '{Java,Spring Boot,Kafka,PostgreSQL,Microservices}'::text[]),
  ('Senior Backend Engineer',      '{Java,Spring Boot,Microservices,AWS,Redis}'::text[]),
  ('Site Reliability Engineer',    '{Kubernetes,Linux,AWS,Terraform}'::text[]),
  ('Security Engineer',            '{Java,Linux,AWS}'::text[]),
  ('Frontend Developer (React)',   '{React,TypeScript,JavaScript,CSS}'::text[]),
  ('Senior UI Engineer',           '{React,TypeScript,CSS,Figma}'::text[]),
  ('Product Designer',             '{Figma}'::text[]),
  ('QA Engineer, Web',             '{Selenium,Manual Testing,JIRA}'::text[]),
  ('Python Developer',             '{Python,Django,PostgreSQL,REST APIs}'::text[]),
  ('Data Engineer',                '{Python,Spark,SQL,AWS}'::text[]),
  ('Fresher Software Engineer',    '{Java,Python,SQL,Git}'::text[]),
  ('Automation Test Engineer',     '{Selenium,Appium,Java,Automation Testing}'::text[]),
  ('Flutter Developer',            '{Flutter,Dart,Firebase}'::text[]),
  ('iOS Developer',                '{Swift,iOS,SwiftUI}'::text[]),
  ('Product Analyst',              '{SQL,Power BI}'::text[]),
  ('Technical Lead, Mobile',       '{Android,Kotlin,Jetpack Compose,Clean Architecture,Agile}'::text[])
) as v(title, skills)
where j.title = v.title
  and j.referrer_id::text like '5eed0000-%';   -- only the seed test jobs

-- Check: every seed job should now list skills
select title, skills from public.jobs where referrer_id::text like '5eed0000-%' order by title;
