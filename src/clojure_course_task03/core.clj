(ns clojure-course-task03.core
  (:require [clojure.set]))

(defn join* [table-name conds]
  (let [op (first conds)
        f1 (name (nth conds 1))
        f2 (name (nth conds 2))]
    (str table-name " ON " f1 " " op " " f2)))

(defn where* [data]
  (let [ks (keys data)
        res (reduce str (doall (map #(let [src (get data %)
                                           v (if (string? src)
                                               (str "'" src "'")
                                               src)]
                                       (str (name %) " = " v ",")) ks)))]
    (reduce str (butlast res))))

(defn order* [column ord]
  (str (name column)
       (if-not (nil? ord) (str " " (name ord)))))

(defn limit* [v] v)

(defn offset* [v] v)

(defn -fields** [data]
  (reduce str (butlast (reduce str (doall (map #(str (name %) ",") data))))))

(defn fields* [flds allowed]
  (let [v1 (apply hash-set flds)
        v2 (apply hash-set allowed)
        v (clojure.set/intersection v1 v2)]
    (cond
     (and (= (first flds) :all) (= (first allowed) :all)) "*"
     (and (= (first flds) :all) (not= (first allowed) :all)) (-fields** allowed)
     (= :all (first allowed)) (-fields** flds)
     :else (-fields** (filter v flds)))))

(defn select* [table-name {:keys [fields where join order limit offset]}]
  (-> (str "SELECT " fields " FROM " table-name " ")
      (str (if-not (nil? where) (str " WHERE " where)))
      (str (if-not (nil? join) (str " JOIN " join)))
      (str (if-not (nil? order) (str " ORDER BY " order)))
      (str (if-not (nil? limit) (str " LIMIT " limit)))
      (str (if-not (nil? offset) (str " OFFSET " offset)))))


(defmacro select [table-name & data]
  (let [;; Var containing allowed fields
        fields-var# (symbol (str table-name "-fields-var"))

        ;; The function takes one of the definitions like (where ...) or (join ...)
        ;; and returns a map item [:where (where* ...)] or [:join (join* ...)].
        transf (fn [elem]
                 (let [v (first elem)
                       v2 (second elem)
                       v3 (if (> (count elem) 2) (nth elem 2) nil)
                       val (case v
                               fields (list 'fields* (vec (next elem)) fields-var#)
                               offset (list 'offset* v2)
                               limit (list 'limit* v2)
                               order (list 'order* v2 v3)
                               join (list 'join* (list 'quote v2) (list 'quote v3))
                               where (list 'where* v2))]
                   [(keyword v) val]))

        ;; Takes a list of definitions like '((where ...) (join ...) ...) and returns
        ;; a vector [[:where (where* ...)] [:join (join* ...)] ...].
        env* (loop [d data
                    v (first d)
                    res []]
               (if (empty? d)
                 res
                 (recur (next d) (second d) (conj res (transf v)))))

        ;; Accepts vector [[:where (where* ...)] [:join (join* ...)] ...],
        ;; returns map {:where (where* ...), :join (join* ...), ...}
        env# (apply hash-map (apply concat env*))]
    
    `(select* ~(str table-name) ~env#)))


;; Examples:
;; -------------------------------------

(let [proposal-fields-var [:person, :phone, :address, :price]]
  (select proposal
          (fields :person, :phone, :id)
          (where {:price 11})
          (join agents (= agents.proposal_id proposal.id))
          (order :f3)
          (limit 5)
          (offset 5)))

(let [proposal-fields-var [:person, :phone, :address, :price]]
  (select proposal
          (fields :all)
          (where {:price 11})
          (join agents (= agents.proposal_id proposal.id))
          (order :f3)
          (limit 5)
          (offset 5)))

(let [proposal-fields-var [:all]]
  (select proposal
          (fields :all)
          (where {:price 11})
          (join agents (= agents.proposal_id proposal.id))
          (order :f3)
          (limit 5)
          (offset 5)))


(comment
  ;; Описание и примеры использования DSL
  ;; ------------------------------------
  ;; Предметная область -- разграничение прав доступа на таблицы в реелтерском агенстве
  ;;
  ;; Работают три типа сотрудников: директор (имеет доступ ко всему), операторы ПК (принимают заказы, отвечают на тел. звонки,
  ;; передают агенту инфу о клиентах), агенты (люди, которые лично встречаются с клиентами).
  ;;
  ;; Таблицы:
  ;; proposal -> [id, person, phone, address, region, comments, price]
  ;; clients -> [id, person, phone, region, comments, price_from, price_to]
  ;; agents -> [proposal_id, agent, done]

  ;; Определяем группы пользователей и
  ;; их права на таблицы и колонки
  (group Agent
         proposal -> [person, phone, address, price]
         agents -> [clients_id, proposal_id, agent])

  ;; Предыдущий макрос создает эти функции
  (select-agent-proposal) ;; select person, phone, address, price from proposal;
  (select-agent-agents) ;; select clients_id, proposal_id, agent from agents;




  (group Operator
         proposal -> [:all]
         clients -> [:all])

  ;; Предыдущий макрос создает эти функции
  (select-operator-proposal) ;; select * proposal;
  (select-operator-clients) ;; select * from clients;



  (group Director
         proposal -> [:all]
         clients -> [:all]
         agents -> [:all])

  ;; Предыдущий макрос создает эти функции
  (select-director-proposal) ;; select * proposal;
  (select-director-clients) ;; select * from clients;
  (select-director-agents) ;; select * from agents;
  

  ;; Определяем пользователей и их группы

  (user Ivanov
        (belongs-to Agent))

  (user Sidorov
        (belongs-to Agent))

  (user Petrov
        (belongs-to Operator))

  (user Directorov
        (belongs-to Operator,
                    Agent,
                    Director))


  ;; Оператор select использует внутри себя переменную <table-name>-fields-var.
  ;; Для указанного юзера макрос with-user должен определять переменную <table-name>-fields-var
  ;; для каждой таблицы, которая должна содержать список допустимых полей этой таблицы
  ;; для этого пользователя.

  ;; Агенту можно видеть свои "предложения"
  (with-user Ivanov
    (select proposal
            (fields :person, :phone, :address, :price)
            (join agents (= agents.proposal_id proposal.id))))

  ;; Агенту не доступны клиенты
  (with-user Ivanov
    (select clients
            (fields :all))) ;; Empty set

  ;; Директор может видеть состояние задач агентов
  (with-user Directorov
    (select agents
            (fields :done)
            (where {:agent "Ivanov"})
            (order :done :ASC)))
  
  )

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; TBD: Implement the following macros
;;
(def ^:dynamic *my-data* (atom {}))
(defmacro group [name & body]
  (let [gr-name# (.toLowerCase (str name))
         gr-key# (keyword (.toLowerCase (str name)))]
      (loop  [res '()  bodypart#  (vec (partition 3 body)) ]
       (if (empty? bodypart#) (conj   res 'do)
           (let [[table# fun# colvec#] (first bodypart#)
          ntable# (str table#)]
         (swap!  *my-data* assoc gr-key# (conj (gr-key# @*my-data*)
                                                   (vector ntable# (vec (map keyword colvec#)))))
         (recur     (conj res `(defn ~(symbol (str "select-"  gr-name# "-" ntable#))
                                    [] (str "SELECT " ~(if (= :all (first colvec#)) "*"
                           (apply str (interpose "," (map str  colvec#)))) " FROM " ~ntable# " ")))
        (next bodypart#)))))))


(defmacro user [name & body]
  ;; Пример
  ;; (user Ivanov
  ;; (belongs-to Agent))
  ;; Создает переменные Ivanov-proposal-fields-var = [:person, :phone, :address, :price]
  ;; и Ivanov-agents-fields-var = [:clients_id, :proposal_id, :agent]
  (let [username# (str name)
        usergroup# (keyword (.toLowerCase (str (last (first body))))) ]
    (when  (= "belongs-to" (str  (first (first body))))
      (loop [res '() vecdata# (usergroup# @*my-data*) ]
        (if (empty? vecdata#) (conj res 'do)
            (let [[table# colvec#] (first vecdata#)]
              (recur (conj res `(def ~(symbol (str username# "-" table# "-fields-var")) ~colvec#)) (next vecdata#))))))))
(defn varlist [name]
  (filter #(re-find (re-pattern (str name)) (str %)) (ns-publics *ns*)))
  

(defmacro with-user [name & body]
  ;; Пример
  ;; (with-user Ivanov
  ;; . . .)
  ;; 1) Находит все переменные, начинающиеся со слова Ivanov, в *user-tables-vars*
  ;; (Ivanov-proposal-fields-var и Ivanov-agents-fields-var)
  ;; 2) Создает локальные привязки без префикса Ivanov-:
  ;; proposal-fields-var и agents-fields-var.
  ;; Таким образом, функция select, вызванная внутри with-user, получает
  ;; доступ ко всем необходимым переменным вида <table-name>-fields-var.
  (let [binvar# (filter #(re-find (re-pattern (str name)) (str %)) (ns-publics *ns*))
        namelen# (inc (count (str name)))]
    `(let ~(loop [res# [] myvar# binvar#]
             (if (empty? myvar#)
               res#
               (let [namevar# (str (first (first myvar#))) nameloc# (subs namevar# namelen#)]
                 (recur  (vec (concat  (vector (symbol nameloc#) (symbol namevar#)) res#)) (next myvar#)))))
                    ~@body  )))
