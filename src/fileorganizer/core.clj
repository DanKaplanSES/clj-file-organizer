(ns fileorganizer.core
  (:require [seesaw.core :refer :all])
  (:require [seesaw.chooser :refer :all])
  (:import [javax.swing JFileChooser])
  (:import [java.awt.event KeyEvent])    
  )

(native!)

(defn file-chooser []
  (JFileChooser.))

(def fc (doto (file-chooser)
          (.setControlButtonsAreShown false)
          (.setMultiSelectionEnabled true)))



(def open-button (button :text "Open"))
(listen open-button :action (fn [e] (alert (.getSelectedFile fc))))
(def delete-button (button :text "Delete"))
(listen delete-button :action (fn [e] (alert (.getSelectedFile fc))))

(defn shortcut-listener [text e]
  (let [modifier (KeyEvent/getKeyModifiersText (.getModifiers e))        
;        ctrl (.isControlDown e)
;        alt (.isAltDown e)
;        shift (.isShiftDown e)
        code (.getKeyCode e)
        key-text (KeyEvent/getKeyText code)
        modifier-pressed (some identity (map #(= code %) [KeyEvent/VK_CONTROL KeyEvent/VK_ALT KeyEvent/VK_SHIFT]))]
    (when-not modifier-pressed
      (config! text :text (.trim (str modifier " " key-text))))))
    

(defn make-shortcut-text []
  (text 
    :editable? false 
    :listen [:key-pressed #(shortcut-listener (.getSource %) %)]))

(def f (frame :title "File Organizer"))

(defn shortcut-ok-button-action [dialog fc shortcut-label shortcut-text destination-button error-label]
  (let [selected-file (.getSelectedFile fc)
        shortcut-string (config shortcut-text :text)]
    (if (and selected-file shortcut-string)
      (do
        (config! shortcut-label :text shortcut-string)
        (config! destination-button :text (.getAbsolutePath selected-file))
        (dispose! dialog))      
      (config! error-label :text "You must choose a file AND pick a shortcut"))))

(defn popup-shortcut-dialog [destination-button shortcut-label]
  (let [d (custom-dialog :title "Choose a Shortcut" :modal? true :parent f)
        fc (doto (file-chooser) (.setControlButtonsAreShown false))
        st (make-shortcut-text)
        shortcut-panel (horizontal-panel :items ["Shortcut:" st])
        error-label (label :foreground :red)
        frame-buttons (horizontal-panel :items [(button :text "OK"
                                                        :listen [:action (fn [e] (shortcut-ok-button-action d fc shortcut-label st destination-button error-label))]) 
                                                (button :text "Cancel"
                                                        :listen [:action (fn [e] (dispose! d))])])]    
    (config! d :content (vertical-panel :items [fc shortcut-panel frame-buttons error-label]))    
    (-> d pack! show!)))	

(defn destination [shortcut-label] 
  (let [destination-button (button :text "<Unset>")]                                          
    (listen destination-button :action (fn [e] (popup-shortcut-dialog destination-button shortcut-label)))
    destination-button))                                       

(def shortcut-label1 (label "<Unset>"))
(def shortcut-label2 (label "<Unset>"))
(def shortcut-label3 (label "<Unset>"))
(def shortcut-label4 (label "<Unset>"))
(def shortcut-label5 (label "<Unset>"))

(def shortcuts (grid-panel :columns 1                               
                           :items ["Shortcut"
                                   shortcut-label1
                                   shortcut-label2
                                   shortcut-label3
                                   shortcut-label4
                                   shortcut-label5]))

(def destinations (grid-panel :columns 1 
                              :items ["Destination"
                                      (destination shortcut-label1)
                                      (destination shortcut-label2)
                                      (destination shortcut-label3)
                                      (destination shortcut-label4)
                                      (destination shortcut-label5)]))



(def destination-shortcuts (horizontal-panel :items 
                                             [(left-right-split 
                                                destinations 
                                                shortcuts 
                                                :divider-location 2/3)]))


(config! f :content (vertical-panel :items [fc                                            
                                            (horizontal-panel :items [open-button delete-button])
                                            (separator)                                            
                                            destination-shortcuts]))

(-> f pack! show!)