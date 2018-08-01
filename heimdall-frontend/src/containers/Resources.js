import React, { Component } from 'react'
import { connect } from 'react-redux'
import { bindActionCreators } from 'redux'
import { getAllResourcesByApi, clearResources, toggleModal, remove } from '../actions/resources'
import PropTypes from 'prop-types'

import {Row, Col, Button, Tooltip, Modal, notification} from 'antd'

import Loading from '../components/ui/Loading'
import Operations from './Operations'
import ResourceForm from '../components/resources/ResourceForm'
import HeimdallCollapse from '../components/collapse'

const HeimdallPanel = HeimdallCollapse.Panel;
const ButtonGroup = Button.Group;

class Resources extends Component {

    constructor(props) {
        super(props)
        this.state = {
            loadedKeys: [],
            resourceSelected: 0
        }

        this.callback = this.callback.bind(this)
        this.addResourceModal = this.addResourceModal.bind(this)
        this.updateResourceModal = this.updateResourceModal.bind(this)
        this.handleSave = this.handleSave.bind(this)
        this.handleCancel = this.handleCancel.bind(this)
    }

    componentDidMount() {
        let idApi = this.props.api.id
        if (idApi) {
            this.props.getResourcesByApi(idApi)
        }
        this.props.toggleModal(false)
    }

    componentWillUnmount() {
        this.props.clearResources()
        // this.setState({...this.state, steps: []})
    }

    componentWillReceiveProps(newProps) {
        if (newProps.notification && newProps.notification !== this.props.notification) {
            const { type, message, description } = newProps.notification
            notification[type]({ message, description })
        }
    }

    callback(keys) {
        keys.forEach(key => {
            //verificar se essa key está presente nos items carregados
            if (!this.state.loadedKeys.includes(key)) {
                //se nao estiver, adicionar as keys que foram abertas e buscar os operations desse recurso.
                this.setState({ ...this.state, loadedKeys: [...this.state.loadedKeys, key] })
            }
        })
    }

    addResourceModal() {
        this.props.toggleModal(true)
    }

    updateResourceModal = (resourceId) => (e) => {
        this.setState({ ...this.state, resourceSelected: resourceId });
        this.props.toggleModal(true)
    }

    handleSave(e) {

        this.addResource.onSubmitResource()
        this.props.clearResources()
    }

    handleCancel(e) {
        this.props.toggleModal(false)
        this.setState({ ...this.state, resourceSelected: 0 });
    }

    remove = (idApi, resourceId) => (e) => {
        this.props.remove(idApi, resourceId)
        this.props.clearResources()
    }

    render() {
        const { api } = this.props
        const { resources } = this.props
        const { loading } = this.props
        if (!resources) return <Loading />

        const modalResource =
            <Modal title="Add Resource"
                footer={[
                    <Button key="back" onClick={this.handleCancel}>Cancel</Button>,
                    <Button key="submit" type="primary" loading={loading} onClick={this.handleSave}>
                        Save
                        </Button>
                ]}
                visible={this.props.visibleModal}
                onCancel={this.handleCancel}
                destroyOnClose >
                <ResourceForm onRef={ref => (this.addResource = ref)} resourceId={this.state.resourceSelected} idApi={api.id} />
            </Modal>

        if (resources && resources.length === 0) {
            return (
                <Row type="flex" justify="center" align="bottom">
                    <Col style={{ marginTop: 20 }}>
                        You don't have resources in this <b>API</b>, please <Button type="dashed" className="add-tour" onClick={this.addResourceModal}>Add Resource</Button>
                    </Col>

                    {modalResource}
                </Row>
            )
        }

        return (
            <Row>
                <HeimdallCollapse onChange={this.callback}>
                    {resources.map((resource, index) => {
                        return (
                            <HeimdallPanel className={index === 0 ? "header-tour" : ''} header={resource.name} key={resource.id} extra={
                                <Row type="flex" justify="center">
                                    <ButtonGroup>
                                        <Tooltip title="Update">
                                            <Button type="primary" icon="edit" onClick={this.updateResourceModal(resource.id)} />
                                        </Tooltip>
                                        <Tooltip title="Delete">
                                            <Button type="danger" icon="delete" onClick={this.remove(api.id, resource.id)} />
                                        </Tooltip>
                                    </ButtonGroup>
                                </Row>
                            } extraWidth={10}>
                                <Operations idResource={resource.id} idApi={api.id} />
                            </HeimdallPanel>
                        )
                    })}
                </HeimdallCollapse>
                <br />
                <Row type="flex" justify="end">
                    <Tooltip title="Add Resource">
                        <Button className="card-button add-tour" type="primary" icon="plus" onClick={this.addResourceModal} size="large" shape="circle" />
                    </Tooltip>
                </Row>

                {modalResource}
            </Row>
        )
    }
}

Resources.propTypes = {
    api: PropTypes.object.isRequired
}

const mapStateToProps = state => {
    return {
        resources: state.resources.resources,
        operations: state.operations.list,
        visibleModal: state.resources.visibleModal,
        loading: state.resources.loading,
        notification: state.resources.notification
    }
}

const mapDispatchToProps = dispatch => {
    return {
        getResourcesByApi: bindActionCreators(getAllResourcesByApi, dispatch),
        clearResources: bindActionCreators(clearResources, dispatch),
        toggleModal: bindActionCreators(toggleModal, dispatch),
        remove: bindActionCreators(remove, dispatch)
    }
}

export default connect(mapStateToProps, mapDispatchToProps)(Resources)