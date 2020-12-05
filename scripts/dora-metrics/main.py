import script
import helper


if __name__ == "__main__":
    api_key = input("Enter API-KEY : ")

    while True:
        file_operation = input("Enter A to append to existing file (leave blank to create new file) : ")

        if file_operation == 'a' or file_operation == 'A' or file_operation == "":
            if file_operation == "":
                file_operation = script.FILE_OPERATION_NEW
            else:
                file_operation = script.FILE_OPERATION_APPEND
            break
        print("ERROR : invalid input, please try again")

    while True:
        filename = input("Enter name of file : ")
        if filename != "":
            break
        print("ERROR : invalid input, please try again")

    entity_id = ""
    while True:
        entity_type = input("Fetch deployments - Enter W for workflow or P for pipeline (leave blank for all) : ")
        if entity_type == "":
            entity_type = script.ENTITY_ALL_EXECUTION
            # fetch all deployments
            break

        if entity_type == 'w':
            entity_type = script.ENTITY_WORKFLOW_EXECUTION
        else:
            entity_type = script.ENTITY_PIPELINE_EXECUTION

        entity_id = input("Enter job id : ")

    while True:
        start_time_input = input("Enter start time of search interval (MM/DD/YYYY) : ")
        # convert string date input to date time obj
        start_time_obj = helper.get_date_obj_from_str(start_time_input, helper.DATE_FORMAT_MM_DD_YYYY)

        if start_time_obj is not None:
            # convert date time object into local timezone
            start_time_obj = helper.convert_date_to_local_timezone(start_time_obj)
            # get epoch time corresponding to local timezone date
            start_time_epoch = int(start_time_obj.timestamp())
            break

        print("ERROR : Date invalid, please try again")

    while True:
        end_time_input = input("Enter end time of search interval (MM/DD/YYYY) (leave blank to search till now) : ")
        if end_time_input == "":
            end_time_epoch = helper.get_current_time_in_epoch_in_seconds()
            end_time_obj = helper.get_date_obj_from_epoch(end_time_epoch)
        else :
            end_time_obj = helper.get_date_obj_from_str(end_time_input, helper.DATE_FORMAT_MM_DD_YYYY)

        if end_time_obj is not None:
            end_time_obj = helper.convert_date_to_local_timezone(end_time_obj)
            end_time_epoch = int(end_time_obj.timestamp())
            break

        print("ERROR : Date invalid, please try again")

    input_arguments = {
        script.ARGS_API_KEY : api_key,
        script.ARGS_FILE_OPERATION : file_operation,
        script.ARGS_FILENAME_KEY : filename,
        script.ARGS_SEARCH_ENTITY_TYPE_KEY : entity_type,
        script.ARGS_SEARCH_ENTITY_ID_KEY : entity_id,
        script.ARGS_SEARCH_INTERVAL_START_TIME_EPOCH_KEY : start_time_epoch,
        script.ARGS_SEARCH_INTERVAL_END_TIME_EPOCH_KEY : end_time_epoch,
    }

    script.compile_data(input_arguments)
