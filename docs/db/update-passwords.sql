-- Hash BCrypt della password "atena" generato con BCryptPasswordEncoder strength 10
-- Aggiornare il valore __BCRYPT_HASH__ con l'output di PasswordHashGenerator prima di eseguire
UPDATE utente SET password_hash = '$2a$10$VkdKwWIQjwaH4Z3LFI0yRejkpiMg2o3wAfaqEEc73lRJFDgQR3p4G'
WHERE password_hash = '{CHANGE_ME}';
