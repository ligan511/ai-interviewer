CREATE DATABASE IF NOT EXISTS ai_interviewer DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
USE ai_interviewer;

CREATE TABLE user (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  username VARCHAR(50) NOT NULL,
  email VARCHAR(120) NOT NULL,
  password_hash VARCHAR(255) NOT NULL,
  status TINYINT NOT NULL DEFAULT 1,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_user_email (email)
) ENGINE=InnoDB;

CREATE TABLE user_resume (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  file_name VARCHAR(255) NOT NULL,
  file_url VARCHAR(500) NOT NULL,
  parsed_json JSON NULL,
  summary TEXT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_resume_user FOREIGN KEY (user_id) REFERENCES user(id),
  KEY idx_resume_user (user_id)
) ENGINE=InnoDB;

CREATE TABLE job (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  name VARCHAR(100) NOT NULL,
  description TEXT,
  level VARCHAR(20) NOT NULL DEFAULT 'junior',
  status TINYINT NOT NULL DEFAULT 1,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_job_status (status)
) ENGINE=InnoDB;

CREATE TABLE job_skill (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  job_id BIGINT NOT NULL,
  skill_name VARCHAR(100) NOT NULL,
  weight DECIMAL(5,2) NOT NULL DEFAULT 1.00,
  CONSTRAINT fk_job_skill_job FOREIGN KEY (job_id) REFERENCES job(id),
  KEY idx_job_skill_job (job_id)
) ENGINE=InnoDB;

CREATE TABLE interview_session (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  job_id BIGINT NOT NULL,
  resume_id BIGINT NULL,
  interview_type VARCHAR(30) NOT NULL DEFAULT 'technical',
  difficulty VARCHAR(20) NOT NULL DEFAULT 'medium',
  question_limit INT NOT NULL DEFAULT 10,
  duration_limit_seconds INT NOT NULL DEFAULT 1800,
  status VARCHAR(20) NOT NULL DEFAULT 'CREATED',
  started_at DATETIME NULL,
  ended_at DATETIME NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_session_user FOREIGN KEY (user_id) REFERENCES user(id),
  CONSTRAINT fk_session_job FOREIGN KEY (job_id) REFERENCES job(id),
  CONSTRAINT fk_session_resume FOREIGN KEY (resume_id) REFERENCES user_resume(id),
  KEY idx_session_user_created (user_id, created_at),
  KEY idx_session_job (job_id),
  KEY idx_session_status (status)
) ENGINE=InnoDB;

CREATE TABLE interview_question (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  session_id BIGINT NOT NULL,
  parent_question_id BIGINT NULL,
  content TEXT NOT NULL,
  question_type VARCHAR(30) NOT NULL,
  difficulty VARCHAR(20) NOT NULL,
  target_skill VARCHAR(100) NULL,
  sequence_no INT NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_question_session FOREIGN KEY (session_id) REFERENCES interview_session(id),
  CONSTRAINT fk_question_parent FOREIGN KEY (parent_question_id) REFERENCES interview_question(id),
  UNIQUE KEY uk_question_sequence (session_id, sequence_no),
  KEY idx_question_parent (parent_question_id)
) ENGINE=InnoDB;

CREATE TABLE interview_answer (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  question_id BIGINT NOT NULL,
  answer_text LONGTEXT NULL,
  audio_url VARCHAR(500) NULL,
  transcript_text LONGTEXT NULL,
  duration_seconds INT NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'SUBMITTED',
  submitted_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_answer_question FOREIGN KEY (question_id) REFERENCES interview_question(id),
  UNIQUE KEY uk_answer_question (question_id)
) ENGINE=InnoDB;

CREATE TABLE answer_evaluation (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  answer_id BIGINT NOT NULL,
  total_score DECIMAL(5,2) NOT NULL,
  professional_score DECIMAL(5,2) NOT NULL,
  logic_score DECIMAL(5,2) NOT NULL,
  completeness_score DECIMAL(5,2) NOT NULL,
  analysis_score DECIMAL(5,2) NOT NULL,
  expression_score DECIMAL(5,2) NOT NULL,
  job_match_score DECIMAL(5,2) NOT NULL,
  strengths JSON NULL,
  weaknesses JSON NULL,
  suggestions JSON NULL,
  reference_answer LONGTEXT NULL,
  model_name VARCHAR(100) NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_evaluation_answer FOREIGN KEY (answer_id) REFERENCES interview_answer(id),
  UNIQUE KEY uk_evaluation_answer (answer_id)
) ENGINE=InnoDB;

CREATE TABLE interview_report (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  session_id BIGINT NOT NULL,
  overall_score DECIMAL(5,2) NOT NULL,
  dimension_scores JSON NOT NULL,
  summary TEXT,
  strengths JSON NULL,
  weaknesses JSON NULL,
  suggestions JSON NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_report_session FOREIGN KEY (session_id) REFERENCES interview_session(id),
  UNIQUE KEY uk_report_session (session_id)
) ENGINE=InnoDB;

CREATE TABLE knowledge_document (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  job_id BIGINT NULL,
  title VARCHAR(255) NOT NULL,
  file_url VARCHAR(500) NOT NULL,
  version VARCHAR(30) NOT NULL DEFAULT '1.0',
  status VARCHAR(20) NOT NULL DEFAULT 'PROCESSING',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_knowledge_job FOREIGN KEY (job_id) REFERENCES job(id),
  KEY idx_knowledge_job_status (job_id, status)
) ENGINE=InnoDB;

CREATE TABLE knowledge_chunk (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  document_id BIGINT NOT NULL,
  chunk_no INT NOT NULL,
  content LONGTEXT NOT NULL,
  embedding_ref VARCHAR(255) NULL,
  metadata_json JSON NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_chunk_document FOREIGN KEY (document_id) REFERENCES knowledge_document(id),
  UNIQUE KEY uk_chunk_no (document_id, chunk_no)
) ENGINE=InnoDB;

CREATE TABLE training_recommendation (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  job_id BIGINT NULL,
  skill_name VARCHAR(100) NOT NULL,
  recommendation_type VARCHAR(30) NOT NULL,
  content TEXT NOT NULL,
  priority VARCHAR(20) NOT NULL DEFAULT 'medium',
  status VARCHAR(20) NOT NULL DEFAULT 'NEW',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_recommendation_user FOREIGN KEY (user_id) REFERENCES user(id),
  CONSTRAINT fk_recommendation_job FOREIGN KEY (job_id) REFERENCES job(id),
  KEY idx_recommendation_user_status (user_id, status)
) ENGINE=InnoDB;
