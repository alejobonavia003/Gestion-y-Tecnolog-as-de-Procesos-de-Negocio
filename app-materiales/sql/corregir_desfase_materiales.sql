-- Corrige el historial de 'preparar_materiales' completado ANTES del arreglo de zona horaria
-- de app-materiales (Motor.java). Solo toca registros con la marca del bug (fin anterior al inicio).
BEGIN;

UPDATE act_hi_actinst
   SET end_time_ = end_time_ + INTERVAL '3 hours',
       duration_ = duration_ + 10800000
 WHERE act_id_ = 'preparar_materiales'
   AND end_time_ < start_time_;

UPDATE act_hi_taskinst
   SET end_time_ = end_time_ + INTERVAL '3 hours',
       duration_ = duration_ + 10800000
 WHERE task_def_key_ = 'preparar_materiales'
   AND end_time_ < start_time_;

UPDATE act_hi_taskinst
   SET claim_time_ = claim_time_ + INTERVAL '3 hours'
 WHERE task_def_key_ = 'preparar_materiales'
   AND claim_time_ < start_time_;

SELECT COUNT(*) AS filas_con_fin_antes_del_inicio
  FROM act_hi_actinst
 WHERE act_id_ = 'preparar_materiales' AND end_time_ < start_time_;

COMMIT;
