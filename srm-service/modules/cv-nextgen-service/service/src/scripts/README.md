# CVNG scripts

## Dashboard creation:
Run ``python3 add_dashboard_json.py`` to generate a dashboard.
#### template files: 
Add conditions for each type in the code and create template file for each type.
#### runtime_metric_definitions
Runtime metric definitions is a generated file for the definitions defined in the code. 

## Installation
1. Install virtualenv
```
python3 -m venv cvng
```
2. Activate the venv
```
source cvng/bin/activate
```
3. Install the requirements
```
pip install -r requirements.txt
```
4. Run the python script
```
python3 add-dashboard-json.py
```

