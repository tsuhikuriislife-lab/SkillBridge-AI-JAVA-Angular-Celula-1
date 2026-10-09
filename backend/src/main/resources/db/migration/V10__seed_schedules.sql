INSERT INTO service_schedules (id, service_id, start_day, session_duration, frequency, number_of_sessions, start_date) VALUES
(gen_random_uuid(), '11111111-1111-1111-1111-111111111111', 'MONDAY', 60, 'WEEKLY', 4, CURRENT_DATE + INTERVAL '7 days'),
(gen_random_uuid(), '22222222-2222-2222-2222-222222222222', 'WEDNESDAY', 90, 'WEEKLY', 2, CURRENT_DATE + INTERVAL '5 days'),
(gen_random_uuid(), '33333333-3333-3333-3333-333333333333', 'FRIDAY', 120, 'BIWEEKLY', 1, CURRENT_DATE + INTERVAL '10 days');
